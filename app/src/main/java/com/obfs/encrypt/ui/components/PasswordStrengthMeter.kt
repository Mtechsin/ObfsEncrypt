package com.obfs.encrypt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Live Password Strength Meter
 * 
 * Why this approach:
 * Provides instant visual feedback to users regarding the security of their input.
 * Animated transitions across states make the UI responsive and polished.
 */
@Composable
fun PasswordStrengthMeter(password: String) {
    val strength = calculateStrength(password)
    
    val targetProgress = when (strength) {
        0 -> 0.0f
        1 -> 0.33f
        2 -> 0.66f
        else -> 1.0f
    }
    
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(300),
        label = "progress"
    )

    val targetColor = when (strength) {
        0 -> Color.Gray
        1 -> MaterialTheme.colorScheme.error
        2 -> Color(0xFFFFA000) // Amber
        else -> MaterialTheme.colorScheme.primary
    }

    val progressColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "color"
    )

    val strengthText = when (strength) {
        0 -> "Enter password"
        1 -> "Weak"
        2 -> "Medium"
        else -> "Strong"
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strengthText,
            style = MaterialTheme.typography.bodySmall,
            color = progressColor
        )
    }
}

private fun calculateStrength(password: String): Int {
    if (password.isEmpty()) return 0
    if (password.length < 6) return 1
    
    var score = 1
    if (password.length >= 10) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    
    return minOf(score, 3)
}
