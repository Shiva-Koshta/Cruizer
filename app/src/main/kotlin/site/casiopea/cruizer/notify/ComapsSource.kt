package site.casiopea.cruizer.notify

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import site.casiopea.cruizer.Session
import site.casiopea.cruizer.proto.TurnKind
import site.casiopea.cruizer.proto.TurnUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Structured live navigation source for CoMaps (the Organic Maps fork), which
 * since v2026.07.16-10 publishes turn-by-turn data on a ContentProvider:
 * content://<package>.provider.navigation/live, guarded by the package's own
 * <package>.permission.READ_NAVIGATION_DATA runtime permission.
 *
 * Machine data (direction enum, exit number, distances) instead of fragile
 * notification text — CoMaps is therefore excluded from TurnSource.
 * Contract per Gadgetbridge's CoMapsNavigationReceiver (merged 2026-07).
 */
object ComapsSource {

    private val PACKAGES = listOf("app.comaps", "app.comaps.fdroid", "app.comaps.google")
    private const val AUTHORITY_SUFFIX = ".provider.navigation"
    private const val PERMISSION_SUFFIX = ".permission.READ_NAVIGATION_DATA"

    // lazy: unit tests exercise only the pure mapping — Handler needs a Looper
    private val observer by lazy { Observer(Handler(Looper.getMainLooper())) }

    @Volatile private var appContext: Context? = null

    /** Installed CoMaps flavours (any fork flavour with the provider present). */
    fun installed(context: Context): List<String> = PACKAGES.filter {
        context.packageManager.resolveContentProvider(it + AUTHORITY_SUFFIX, 0) != null
    }

    fun granted(context: Context, pkg: String): Boolean =
        context.checkSelfPermission(pkg + PERMISSION_SUFFIX) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun permissions(context: Context): List<String> =
        installed(context).filter { !granted(context, it) }.map { it + PERMISSION_SUFFIX }

    fun permission(pkg: String) = pkg + PERMISSION_SUFFIX

    private var registered = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (registered) return
        val app = context.applicationContext
        appContext = app
        scope.launch {
            if (registered) return@launch
            val flavors = installed(app).filter { granted(app, it) }
            if (flavors.isEmpty()) return@launch
            var ok = 0
            flavors.forEach { pkg ->
                try {
                    app.contentResolver.registerContentObserver(
                        Uri.parse("content://$pkg$AUTHORITY_SUFFIX/live"),
                        false,
                        observer,
                    )
                    ok++
                } catch (_: Exception) {
                }
            }
            if (ok > 0) {
                registered = true
                observer.query(app) // surface a session already in progress
            }
        }
    }

    fun stop(context: Context) {
        if (!registered) return
        try {
            context.applicationContext.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        registered = false
        observer.dropOwnTurn()
    }

    /**
     * Provider change -> query, throttled like Gadgetbridge (the provider fires
     * per route change, which can burst several times a second).
     */
    private class Observer(private val handler: Handler) : ContentObserver(handler) {
        private var pending = false
        private var lastQueryAt = 0L
        private var owned = false // whether the last posted turn came from CoMaps

        override fun onChange(selfChange: Boolean) {
            val ctx = appContext ?: return
            if (System.currentTimeMillis() - lastQueryAt < 250) {
                if (!pending) {
                    pending = true
                    handler.postDelayed({ pending = false; query(ctx) }, 250)
                }
                return
            }
            lastQueryAt = System.currentTimeMillis()
            query(ctx)
        }

        fun query(ctx: Context) {
            scope.launch {
                val pkg = installed(ctx).firstOrNull { granted(ctx, it) } ?: return@launch
                try {
                    val cursor = ctx.contentResolver.query(
                        Uri.parse("content://$pkg$AUTHORITY_SUFFIX/live"),
                        null, null, null, null,
                    )
                    val row = cursor?.use { c ->
                        if (!c.moveToFirst()) null else Row(
                            car = c.col("car_direction"),
                            ped = c.col("pedestrian_direction"),
                            distToTurn = c.col("dist_to_turn"),
                            distToTarget = c.col("dist_to_target"),
                            timeSeconds = c.intOrNull("total_time_seconds"),
                            exitNum = c.intOrNull("exit_num"),
                        )
                    }
                    val update = row?.let { turnUpdate(it.car, it.ped, it.distToTurn, it.distToTarget, it.timeSeconds, it.exitNum) }
                    when {
                        update != null -> {
                            Session.navActive.value = true
                            Session.postTurn(update)
                            owned = true
                        }
                        owned -> {
                            // empty cursor = navigation ended; clear only our own turn
                            Session.navActive.value = false
                            Session.postTurn(null)
                            owned = false
                        }
                    }
                } catch (_: SecurityException) {
                    // rider revoked CoMaps permission mid-session; drop own turn safely
                    if (owned) {
                        Session.navActive.value = false
                        Session.postTurn(null)
                        owned = false
                    }
                } catch (_: Exception) {
                }
            }
        }

        fun dropOwnTurn() {
            if (owned) {
                Session.navActive.value = false
                Session.postTurn(null)
            }
            owned = false
        }
    }

    private data class Row(
        val car: String?,
        val ped: String?,
        val distToTurn: String?,
        val distToTarget: String?,
        val timeSeconds: Int?,
        val exitNum: Int?,
    )

    private fun android.database.Cursor.col(name: String): String? =
        getColumnIndex(name)?.takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    private fun android.database.Cursor.intOrNull(name: String): Int? =
        getColumnIndex(name)?.takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }

    /** Pure direction/distance -> TurnUpdate mapping; also used by the unit tests. */
    fun turnUpdate(
        car: String?,
        ped: String?,
        distToTurn: String?,
        distToTarget: String?,
        timeSeconds: Int?,
        exitNum: Int?,
    ): TurnUpdate? {
        val kind = CAR_KIND[car] ?: PED_KIND[ped] ?: return null
        return TurnUpdate(
            kind = kind,
            distanceM = distToTurn?.let(NavTextParser::metersOf),
            exit = if (kind == TurnKind.ROUNDABOUT) exitNum else null,
            next = null,
            nextDistanceM = null,
            arrivalSecondsFromNow = timeSeconds,
            routeRemainingM = distToTarget?.let(NavTextParser::metersOf),
            remainingSeconds = timeSeconds,
            ended = kind == TurnKind.DESTINATION,
        )
    }

    /** car_direction enum -> TurnKind (CoMaps crashes out of routing mid-way otherwise). */
    private val CAR_KIND = mapOf(
        "GO_STRAIGHT" to TurnKind.STRAIGHT,
        "TURN_RIGHT" to TurnKind.RIGHT,
        "TURN_SHARP_RIGHT" to TurnKind.SHARP_RIGHT,
        "TURN_SLIGHT_RIGHT" to TurnKind.SLIGHT_RIGHT,
        "TURN_LEFT" to TurnKind.LEFT,
        "TURN_SHARP_LEFT" to TurnKind.SHARP_LEFT,
        "TURN_SLIGHT_LEFT" to TurnKind.SLIGHT_LEFT,
        "U_TURN_LEFT" to TurnKind.U_TURN,
        "U_TURN_RIGHT" to TurnKind.U_TURN,
        "ENTER_ROUND_ABOUT" to TurnKind.ROUNDABOUT,
        "LEAVE_ROUND_ABOUT" to TurnKind.ROUNDABOUT,
        "STAY_ON_ROUND_ABOUT" to TurnKind.STRAIGHT,
        "START_AT_END_OF_STREET" to TurnKind.DEPART,
        "REACHED_YOUR_DESTINATION" to TurnKind.DESTINATION,
        "EXIT_HIGHWAY_TO_LEFT" to TurnKind.OFF_RAMP_LEFT,
        "EXIT_HIGHWAY_TO_RIGHT" to TurnKind.OFF_RAMP_RIGHT,
    )

    /** pedestrian_direction enum (fallback when there is no car direction). */
    private val PED_KIND = mapOf(
        "NO_TURN" to TurnKind.STRAIGHT,
        "GO_STRAIGHT" to TurnKind.STRAIGHT,
        "TURN_RIGHT" to TurnKind.RIGHT,
        "TURN_LEFT" to TurnKind.LEFT,
        "REACHED_YOUR_DESTINATION" to TurnKind.DESTINATION,
    )
}
