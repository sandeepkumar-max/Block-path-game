package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = Player1Color,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
