package com.whiskeymike.wmpoketrap.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF8B5CF6)
private val PurpleDark = Color(0xFF6D28D9)
private val Bg = Color(0xFF07070A)
private val Panel = Color(0xFF111116)
private val Text = Color(0xFFF4F4F5)
private val Muted = Color(0xFFA1A1AA)
private val Success = Color(0xFF22C55E)

private val Scheme = darkColorScheme(
    primary = Purple,
    onPrimary = Text,
    secondary = PurpleDark,
    background = Bg,
    surface = Panel,
    onBackground = Text,
    onSurface = Text,
    onSurfaceVariant = Muted,
    tertiary = Success,
)

@Composable
fun WmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
