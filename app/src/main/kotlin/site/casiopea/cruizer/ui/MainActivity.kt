package site.casiopea.cruizer.ui

import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import site.casiopea.cruizer.NavRoute
import site.casiopea.cruizer.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Launcher. First-run onboarding gate, then two tabs: Bike / Settings (playground is a debug overlay). */
class MainActivity : ComponentActivity() {

    /** Tabs; the debug-only playground is a boolean, not a page. */
    companion object {
        /**
         * Bumped on every resume and after permission dialogs — code reading it
         * (the onboarding gate) recomposes and re-checks preconditions.
         */
        val gate = MutableStateFlow(0)

        private val TAB_ITEMS = listOf(
            0 to ("Bike" to Icons.Filled.Home),
            1 to ("Settings" to Icons.Filled.Settings),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request 120Hz display refresh rate and touch frame rate boost for GrapheneOS & Pixel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 120f
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.setFrameRateBoostOnTouchEnabled(true)
            window.decorView.setRequestedFrameRate(120f)
        }
        // Settings persist from here, not from the Pod service, so theme changes
        // survive even when the rider never connects.
        Session.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val theme by Session.themeMode.collectAsState()
            val dark = when (theme) {
                Session.ThemeMode.LIGHT -> false
                Session.ThemeMode.DARK -> true
                Session.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            CruiserTheme(dark) {
                val g by gate.collectAsState()
                @Suppress("UNUSED_EXPRESSION")
                g // re-check on gate update
                val onboarded by Session.onboardingCompleted.collectAsState()
                val ready = hasRequiredPermissions()
                if (!onboarded || !ready) {
                    OnboardingScreen(
                        onFinish = { Session.completeOnboarding() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Tabs()
                }
                if (Session.awaitingPin.collectAsState().value) PinDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.decorView.setRequestedFrameRate(120f)
        }
        gate.value = gate.value + 1 // permission state may have changed off-screen
        site.casiopea.cruizer.notify.TurnSource.rebind(this)
        site.casiopea.cruizer.notify.ComapsSource.start(this)
    }

    @Composable
    private fun Tabs(modifier: Modifier = Modifier) {
        val pagerState = rememberPagerState(initialPage = 0) { 2 } // 0 = Bike, 1 = Settings
        val scope = rememberCoroutineScope()
        var inPlayground by remember { mutableStateOf(false) }

        var backProgress by remember { mutableFloatStateOf(0f) }
        var backGestureActive by remember { mutableStateOf(false) }

        val lockoutNestedScroll = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Absorb any leftover vertical overscroll so it never bleeds into horizontal pager drag
                    return Offset(0f, available.y)
                }
            }
        }

        // Predictive back: active on Playground or on Settings (settledPage != 0).
        // backGestureActive keeps it enabled while the pager itself is being driven,
        // because requestScrollToPage flips settledPage to 0 mid-gesture.
        val isBackEnabled = inPlayground || backGestureActive || pagerState.settledPage != 0
        PredictiveBackHandler(enabled = isBackEnabled) { progress: Flow<BackEventCompat> ->
            val fromPlayground = inPlayground
            backGestureActive = true
            try {
                progress.collect { backEvent ->
                    backProgress = backEvent.progress
                    // Drive the pager itself so the Bike screen is revealed underneath.
                    // pageOffsetFraction must stay within -0.5..0.5.
                    if (!fromPlayground) {
                        val position = (1f - backEvent.progress).coerceIn(0f, 1f)
                        if (position >= 0.5f) {
                            pagerState.requestScrollToPage(1, position - 1f)
                        } else {
                            pagerState.requestScrollToPage(0, position)
                        }
                    }
                }
                if (fromPlayground) {
                    inPlayground = false
                    backProgress = 0f
                    backGestureActive = false
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                        backProgress = 0f
                        backGestureActive = false
                    }
                }
            } catch (_: CancellationException) {
                // Gesture cancelled: unwind the pager without snapping the parallax.
                if (fromPlayground) {
                    backProgress = 0f
                    backGestureActive = false
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                        backProgress = 0f
                        backGestureActive = false
                    }
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Under the playground overlay the pager is the previous screen: slow pull-out.
            val pagerModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val under = inPlayground && backGestureActive
                    translationX = if (under) -(1f - backProgress) * size.width * 0.25f else 0f
                    val scale = if (under) 0.94f + 0.06f * backProgress else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .nestedScroll(lockoutNestedScroll)

            // Read from the pager, not the gesture, so the parallax stays smooth while it settles.
            val gestureProgress = if (backGestureActive && !inPlayground) {
                (1f - (pagerState.currentPage + pagerState.currentPageOffsetFraction))
                    .coerceIn(0f, 1f)
            } else 0f

            // Main content: HorizontalPager holding Bike (0) and Settings (1)
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = pagerModifier,
            ) { pageIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val active = !inPlayground && backGestureActive
                            if (active && pageIndex == 0) {
                                // Previous screen: pulls out from -25% and scales up to 1.
                                translationX = (1f - gestureProgress) * size.width * 0.75f
                                val scale = 0.94f + 0.06f * gestureProgress
                                scaleX = scale
                                scaleY = scale
                            } else {
                                translationX = 0f
                                scaleX = 1f
                                scaleY = 1f
                            }
                            // Current screen: rounds and lifts as it slides right.
                            val lifted = active && pageIndex == 1 && gestureProgress > 0f
                            clip = lifted
                            shadowElevation = if (lifted) 16.dp.toPx() else 0f
                            if (lifted) shape = RoundedCornerShape((gestureProgress * 24f).dp)
                        },
                ) {
                    when (pageIndex) {
                        0 -> PodScreen()
                        1 -> SettingsScreen(onPlayground = { inPlayground = true })
                    }
                    if (!inPlayground && backGestureActive && pageIndex == 0) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = (1f - gestureProgress) * 0.25f)),
                        )
                    }
                }
            }

            // Dim the previous screen while the playground slides away.
            if (inPlayground && backGestureActive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - backProgress) * 0.25f)),
                )
            }

            // Playground overlay screen (with 120fps hardware-accelerated slide)
            if (inPlayground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .graphicsLayer {
                            translationX = backProgress * size.width
                            val lifted = backProgress > 0f
                            clip = lifted
                            shadowElevation = if (lifted) 16.dp.toPx() else 0f
                            if (lifted) shape = RoundedCornerShape((backProgress * 24f).dp)
                        },
                ) {
                    PlaygroundScreen(onBack = { inPlayground = false })
                }
            }

            // Truly Floating Capsule Pill Bar (Transparent surround, shadow + tonal elevation)
            if (!inPlayground) {
                FloatingPillBar(
                    currentPage = pagerState.currentPage,
                    onSelectPage = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @Composable
    private fun FloatingPillBar(
        currentPage: Int,
        onSelectPage: (Int) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val isGlass by Session.isLiquidGlass.collectAsState()
        Surface(
            shape = CircleShape,
            color = if (isGlass) Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            tonalElevation = if (isGlass) 0.dp else 8.dp,
            shadowElevation = if (isGlass) 0.dp else 12.dp,
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .height(60.dp)
                .liquidGlass(isGlass),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TAB_ITEMS.forEach { (index, pair) ->
                    val (label, icon) = pair
                    val isSelected = currentPage == index
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSelectPage(index)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            AnimatedVisibility(visible = isSelected) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}
