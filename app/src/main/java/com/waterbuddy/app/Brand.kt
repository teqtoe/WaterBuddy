package com.waterbuddy.app

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * ═══════════════════════════════════════════════
 *  TEQTOE BRAND — நிறங்கள் எல்லாம் இங்க மட்டும் தான்.
 *  உண்மையான brand color codes கிடைச்சதும் இந்த values-ஐ மாத்தினா போதும்,
 *  முழு app-உம் தானா மாறிடும்.
 * ═══════════════════════════════════════════════
 */
object Brand {
    const val COMPANY = "Teqtoe"
    const val WEBSITE = "teqtoe.com"
    const val WEBSITE_URL = "https://teqtoe.com"

    val Primary = Color(0xFF4F46E5)       // முக்கிய நிறம் (header, buttons)
    val PrimaryDark = Color(0xFF3730A3)
    val Secondary = Color(0xFF8B5CF6)     // accent
    val Accent = Color(0xFFFF6B3D)        // highlight (pause, warnings)
    val Ink = Color(0xFF1E1B3A)           // முக்கிய எழுத்து
    val Muted = Color(0xFF6B6888)         // இரண்டாம் எழுத்து
    val Background = Color(0xFFF5F4FD)
    val Card = Color.White
    val Water = Color(0xFF3FA9F5)
    val WaterDeep = Color(0xFF1C7ED6)

    val HeaderGradient = Brush.linearGradient(listOf(Primary, Secondary))
    val SkyGradient = Brush.verticalGradient(listOf(Color(0xFFDDEBFF), Color(0xFFF3F6FD)))
}
