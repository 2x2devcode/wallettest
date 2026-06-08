package com.twox2.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF1A237E)
private val Secondary = Color(0xFF3949AB)
private val Accent = Color(0xFFFFD54F)

private val DarkColors = darkColorScheme(
    primary = Secondary,
    secondary = Accent,
    tertiary = Color(0xFF5C6BC0),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22)
)

private val LightColors = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Accent,
    background = Color(0xFFF5F7FA),
    surface = Color.White
)

@Composable
fun TwoX2WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
