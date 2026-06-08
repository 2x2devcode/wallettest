package com.twox2.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = TealPrimary,
    onPrimary = Color(0xFF050B13),
    secondary = GreenAccent,
    tertiary = TealDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    error = RedSent
)

private val LightColors = lightColorScheme(
    primary = TealDark,
    secondary = GreenAccent,
    background = Color(0xFFF5F7FA),
    surface = Color.White
)

@Composable
fun TwoX2WalletTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
