package com.bankingpages.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One set of springs for the whole app, so every bounce feels related. */
object Motion {
    fun <T> bouncy(visibilityThreshold: T? = null) = spring(dampingRatio = 0.55f, stiffness = 380f, visibilityThreshold = visibilityThreshold)
    fun <T> smooth(visibilityThreshold: T? = null) = spring(dampingRatio = 0.85f, stiffness = 300f, visibilityThreshold = visibilityThreshold)
    fun <T> snappy(visibilityThreshold: T? = null) = spring(dampingRatio = 0.75f, stiffness = 800f, visibilityThreshold = visibilityThreshold)
    fun <T> push(visibilityThreshold: T? = null) = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = visibilityThreshold)
    const val STAGGER_MS = 45L
    /** Content waits for the screen push to settle before it cascades in. */
    const val AFTER_PUSH_MS = 230L
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val cr = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Shrinks a touch on press and springs back with a little overshoot. Layer-only: no relayout. */
fun Modifier.pressBounce(source: MutableInteractionSource, pressedScale: Float = 0.95f) = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) Motion.snappy() else Motion.bouncy(),
        label = "pressScale"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Tap (and optional long-press) with the bounce and a light haptic tick instead of a ripple.
 * Every tap also plays a full dip-and-spring-back, so even the quickest touch visibly lands.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    pressedScale: Float = 0.95f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = composed {
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    val tapped = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (!reduce) scope.launch {
            // Small controls dip further than big cards, which only need a nudge.
            pop.animateTo(pressedScale - 0.05f, tween(80, easing = FastOutSlowInEasing))
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))
        }
        onClick()
    }
    this
        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        .pressBounce(source, pressedScale)
        .then(
            if (onLongClick != null) Modifier.combinedClickable(
                interactionSource = source, indication = null, enabled = enabled,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
                onClick = tapped
            ) else Modifier.clickable(source, indication = null, enabled = enabled, onClick = tapped)
        )
}

/**
 * Rises into place once. [seen] outlives the list, so scrolling back or
 * returning to the screen never replays it; a newly added item still arrives.
 */
fun Modifier.entrance(index: Int, key: Any, seen: MutableSet<Any>, baseDelay: Long = Motion.AFTER_PUSH_MS) = composed {
    val reduce = LocalReduceMotion.current
    val first = remember(key) { seen.add(key) }
    val p = remember(key) { Animatable(if (first && !reduce) 0f else 1f) }
    LaunchedEffect(key) {
        if (p.value < 1f) {
            delay(baseDelay + index.coerceAtMost(10) * Motion.STAGGER_MS)
            p.animateTo(1f, Motion.smooth())
        }
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 36.dp.toPx()
        val s = 0.94f + 0.06f * v
        scaleX = s; scaleY = s
    }
}

/** A springy pop-in for hero elements (logos, icons) on first show. */
fun Modifier.popIn(delayMs: Long = Motion.AFTER_PUSH_MS) = composed {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) { delay(delayMs); p.animateTo(1f, Motion.bouncy()) }
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val s = 0.6f + 0.4f * v
        scaleX = s; scaleY = s
    }
}
