package com.obfs.encrypt.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState

object Motion {
    // Default spring for smooth, snappy UI
    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val ExtremeSpring = spring<Float>(
        dampingRatio = 0.4f,
        stiffness = Spring.StiffnessMedium
    )

    // Nav bar icon bounce — snappy with slight overshoot
    val NavIconBounceSpring = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = Spring.StiffnessMedium
    )

    // Nav bar icon rotation wiggle — settles quickly
    val NavIconWiggleSpring = spring<Float>(
        dampingRatio = 0.35f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Sliding pill indicator — elastic with satisfying overshoot
    val IndicatorSlideSpring = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessLow
    )

    // Transitions
    val FadeIn = fadeIn(animationSpec = tween(500))
    val FadeOut = fadeOut(animationSpec = tween(500))
    
    val ScaleIn = scaleIn(initialScale = 0.92f, animationSpec = SnappySpring)
    val ScaleOut = scaleOut(targetScale = 0.92f, animationSpec = SnappySpring)
}

fun Modifier.bounceClick() = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = Motion.BouncySpring,
        label = "bounce_click_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) {
        // Handle click if needed, but usually used with another clickable or card
    }
}

// A more refined bounce for cards
fun Modifier.pressClickEffect() = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = Motion.SnappySpring,
        label = "press_click_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
