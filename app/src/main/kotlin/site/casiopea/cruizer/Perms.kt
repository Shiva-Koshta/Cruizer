package site.casiopea.cruizer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Runtime permissions per Android version. Android 11 has no Nearby-devices
 * permissions (BLUETOOTH/BLUETOOTH_ADMIN are install-time) but scanning needs
 * fine location; POST_NOTIFICATIONS only exists from Android 13. Checking a
 * permission the platform doesn't define always reads as denied.
 */
@SuppressLint("InlinedApi") // permission names are plain strings, only used on SDKs that define them
object Perms {
    /** Android 11 and older: BLE scanning is gated on location (permission + Location on). */
    fun locationScan(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk < Build.VERSION_CODES.S

    /** Needed for GATT client/server calls. */
    fun connect(sdk: Int = Build.VERSION.SDK_INT): List<String> =
        if (locationScan(sdk)) emptyList() else listOf(Manifest.permission.BLUETOOTH_CONNECT)

    /** Needed to scan for and connect to the Pod. */
    fun ble(sdk: Int = Build.VERSION.SDK_INT): List<String> =
        if (locationScan(sdk)) listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else connect(sdk) + Manifest.permission.BLUETOOTH_SCAN

    fun notifications(sdk: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    /** Everything the Connect button and onboarding gate on (notification listener aside). */
    fun required(sdk: Int = Build.VERSION.SDK_INT): List<String> = ble(sdk) + notifications(sdk)

    fun Context.granted(perms: List<String>): Boolean =
        perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}
