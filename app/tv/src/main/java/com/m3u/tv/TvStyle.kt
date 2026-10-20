package com.m3u.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Authentic TiviMate Premium TV Palette — Broadcast Grade Obsidian & Slate Edition.
 * Pure deep matte charcoal obsidian base, refined gunmetal card surfaces,
 * subtle 12% hairline borders, elegant titanium/slate focus states (no oversaturated neon),
 * and crisp typography.
 */
object TvColors {
    // Deep matte obsidian and graphite base
    val Background = Color(0xFF0C0E14)
    val BackgroundSoft = Color(0xFF12161F)
    val Surface = Color(0xFF161B24)
    val SurfaceRaised = Color(0xFF1C222E)
    val SurfaceHighlight = Color(0xFF242C3B)
    val SurfaceGlass = Color(0x99161B24)

    // Subtle, elegant borders (never glaring or oversaturated)
    val BorderSubtle = Color(0x1FFFFFFF) // Delicate 12% white hairline
    val BorderGlass = Color(0x33FFFFFF)  // 20% white highlight

    // Authentic TiviMate Focus & Accent — Sophisticated Slate Blue & Titanium
    val Focus = Color(0xFF1E3A5F)         // Deep refined TV slate blue
    val FocusSurface = Color(0xFF223249)  // Card highlight on focus
    val FocusGlow = Color(0xFF3B82F6)     // Subtle 1.5dp focus ring (clean, not neon)
    val Accent = Color(0xFF2563EB)        // Clean accent
    val Danger = Color(0xFFDC2626)        // Clean crimson (LIVE badge & Record)
    val Warning = Color(0xFFF59E0B)       // Amber/Orange (Timeshift & status)
    val Success = Color(0xFF10B981)

    // Typography
    val OnFocus = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8) // Neutral slate silver
    val TextMuted = Color(0xFF64748B)     // Graphite muted
    val BadgeBackground = Color(0xFF1E2532)
    val TimelineMarker = Color(0xFF2563EB)
}

object TvFonts {
    val Body = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semi_bold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    val Accent = FontFamily(
        Font(R.font.lexend_exa_medium, FontWeight.Medium)
    )
}

enum class TvDestination(
    val icon: ImageVector
) {
    Home(Icons.Rounded.Home),
    Library(Icons.AutoMirrored.Rounded.PlaylistPlay),
    Favorites(Icons.Rounded.Favorite),
    Status(Icons.Rounded.Settings)
}

enum class TvSurface {
    Browse,
    Player
}
