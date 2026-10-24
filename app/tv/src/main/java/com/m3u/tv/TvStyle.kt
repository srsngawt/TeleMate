package com.m3u.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Authentic TiviMate Luxury OLED & Smoked Glass Edition.
 * Deep obsidian base, hardware-accelerated translucent glass surfaces (60fps on TV),
 * crystalline hairlines, and illuminated royal sapphire focus system.
 */
object TvColors {
    // OLED Midnight Obsidian Base
    val Background = Color(0xFF07090E)        // Deep obsidian pitch (97% black with subtle midnight undertone)
    val BackgroundSoft = Color(0xFF0C1017)    // Soft midnight depth

    // Smooth zero-cost vertical gradient for cinematic depth
    val RootBackground = Brush.verticalGradient(
        listOf(
            Color(0xFF0D121D),
            Color(0xFF07090E),
            Color(0xFF040608)
        )
    )

    // Hardware-accelerated Smoked Midnight Glass surfaces (60fps guaranteed on TV chipsets)
    val Surface = Color(0xC7101622)          // Smoked midnight frosted glass (~78% alpha)
    val SurfaceRaised = Color(0xD9151F2E)    // Elevated glass tile (~85% alpha)
    val SurfaceHighlight = Color(0xEB1D2A3E) // Illuminated surface for selected/active items
    val SurfaceGlass = Color(0xB30C121D)     // Translucent frosted glass for navigation rail & overlays

    // Crystalline Luminous Hairlines (subtle glass reflection edges, no harsh opaque borders)
    val BorderSubtle = Color(0x18FFFFFF)     // 9.5% white crystal hairline
    val BorderGlass = Color(0x2EFFFFFF)      // 18% frosted glass bevel highlight
    val BorderActive = Color(0x8038BDF8)     // Luminous cyan-azure edge for active/playing channel

    // High-End TV Focus System (Apple TV / Sony Bravia style: vibrant, crisp, instantly visible)
    val Focus = Color(0xFF1D4ED8)            // Vibrant Royal Sapphire
    val FocusSurface = Color(0xFF1E3A8A)     // Luminous navy under focus
    val FocusGlow = Color(0xFF60A5FA)        // Electric Sky Azure 1.5dp focus glow
    val OnFocus = Color(0xFFFFFFFF)

    // Refined Broadcast Accents
    val Accent = Color(0xFF38BDF8)           // Electric Cyan/Azure
    val Danger = Color(0xFFEF4444)           // Vibrant Coral Crimson (LIVE badges & Recording)
    val Warning = Color(0xFFF59E0B)          // Warm Amber Gold (Timeshift status)
    val Success = Color(0xFF10B981)          // Vibrant Emerald

    // Crisp High-Contrast Typography
    val TextPrimary = Color(0xFFF8FAFC)      // Pure Porcelain White
    val TextSecondary = Color(0xFF94A3B8)    // Refined Slate Silver
    val TextMuted = Color(0xFF64748B)        // Graphite Slate
    val BadgeBackground = Color(0x24FFFFFF)  // Frosted translucent glass pill
    val TimelineMarker = Color(0xFF38BDF8)   // Current timeline hour marker
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
