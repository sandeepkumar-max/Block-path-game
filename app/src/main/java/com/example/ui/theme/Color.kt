package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val Player1Color = Color(0xFF1976D2) // Classic Blue
val Player2Color = Color(0xFFD32F2F) // Classic Red
val BoardBackground = Color(0xFFF1EBCB) // Classic Cream Wood
val GridLineColor = Color(0xFF8D6E63) // Classic Brown
val WallColor = Color(0xFF3E2723) // Dark Wood
val AppBackground = Color(0xFFFFFFFF) // Pure White
val AccentGreen = Color(0xFF10B981) // Emerald Green
val ErrorRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF22C55E)

// Vibrant Board Themes
enum class BoardTheme(val displayName: String, val description: String) {
    VIBRANT_NEON("Vibrant Neon", "High-contrast electric grid with glowing accents"),
    CLASSIC_GRID("Classic Grid", "Traditional warm tournament board with crisp lines"),
    EMERALD_NIGHT("Emerald Arena", "Deep forest emerald with crisp gold & white lines"),
    SUNSET_EMBER("Sunset Ember", "Warm vibrant amber & coral high-contrast palette")
}

val ObstacleColor = Color(0xFFD97706) // Vibrant amber-orange for dynamic obstacles

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
