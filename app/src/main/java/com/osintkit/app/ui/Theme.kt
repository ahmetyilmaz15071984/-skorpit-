package com.osintkit.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.osintkit.app.core.Found

private val Dark = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA7F3EB),
    secondary = Color(0xFF9CC9F5),
    background = Color(0xFF0F1418),
    surface = Color(0xFF0F1418),
    surfaceVariant = Color(0xFF1E262C),
    surfaceContainer = Color(0xFF171E23),
    surfaceContainerLow = Color(0xFF151B20),
    surfaceContainerHigh = Color(0xFF1E262C),
    surfaceContainerHighest = Color(0xFF263038),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF9FB0BC),
    outlineVariant = Color(0xFF2E3A43),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBE4),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF2B6CB0),
)

@Composable
fun OsintTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Composable
fun statusColor(s: Found): Color {
    val dark = isSystemInDarkTheme()
    return when (s) {
        Found.FOUND -> if (dark) Color(0xFF66D17A) else Color(0xFF2E7D32)
        Found.NOT_FOUND -> MaterialTheme.colorScheme.onSurfaceVariant
        Found.UNKNOWN -> if (dark) Color(0xFFF2C14E) else Color(0xFFB26A00)
        Found.MANUAL -> if (dark) Color(0xFF9CC9F5) else Color(0xFF2B6CB0)
        Found.SKIPPED -> MaterialTheme.colorScheme.outline
    }
}

fun statusLabel(s: Found): String = when (s) {
    Found.FOUND -> "VAR"
    Found.NOT_FOUND -> "YOK"
    Found.UNKNOWN -> "?"
    Found.MANUAL -> "ELLE"
    Found.SKIPPED -> "—"
}
