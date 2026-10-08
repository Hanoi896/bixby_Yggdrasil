package com.huginmunin.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Fade in animation for screen transitions
 */
@Composable
fun FadeInAnimation(
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 300, easing = EaseInOut)
        ),
        content = { content() }
    )
}

/**
 * Slide up animation
 */
@Composable
fun SlideUpAnimation(
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 400, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(durationMillis = 400)),
        content = { content() }
    )
}

/**
 * Scale animation for buttons
 */
@Composable
fun ScaleAnimation(
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        content = { content() }
    )
}

/**
 * Crossfade transition
 */
@Composable
fun <T> CrossfadeTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    Crossfade(
        targetState = targetState,
        animationSpec = tween(durationMillis = 300),
        modifier = modifier,
        content = content
    )
}
