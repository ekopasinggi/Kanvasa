package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Clean Architectural Studio Core Palette
val StudioPaperBg = Color(0xFFFAF9F6)
val StudioSurface = Color(0xFFFFFFFF)
val StudioSurfaceContainerLow = Color(0xFFF5F4F0)
val StudioSurfaceContainer = Color(0xFFF0EFEA)
val StudioSurfaceContainerHigh = Color(0xFFE6E4DD)
val StudioOutline = Color(0xFFD5D2C8)
val StudioOutlineSubtle = Color(0xFFE5E2D9)

val DeepInkPrimary = Color(0xFF1E293B)
val DeepInkOnPrimary = Color(0xFFFFFFFF)
val DeepInkContainer = Color(0xFFDCE4F2)
val OnDeepInkContainer = Color(0xFF0F172A)

val CobaltAccent = Color(0xFF2563EB)
val OnCobaltAccent = Color(0xFFFFFFFF)
val CobaltContainer = Color(0xFFDBEAFE)
val OnCobaltContainer = Color(0xFF1E3A8A)

val TerracottaAccent = Color(0xFFEA580C)
val OnTerracottaAccent = Color(0xFFFFFFFF)
val TerracottaContainer = Color(0xFFFFEDD5)
val OnTerracottaContainer = Color(0xFF7C2D12)

val TextPrimaryInk = Color(0xFF18202C)
val TextSecondarySlate = Color(0xFF525E70)
val DangerCrimson = Color(0xFFDC2626)

// Curated Canvas Ink Swatches
val InkSwatches: List<Long> = listOf(
    0xFF1E293BL, // Charcoal Ink
    0xFF2563EBL, // Studio Cobalt
    0xFFDC2626L, // Carmine Red
    0xFF059669L, // Emerald Green
    0xFF7C3AEDL, // Royal Violet
    0xFFEA580CL  // Terracotta Orange
)

// Curated Highlighter Swatches (Opaque base colors; rendered with translucent alpha)
val HighlighterSwatches: List<Long> = listOf(
    0xFFFACC15L, // Warm Lemon
    0xFF4ADE80L, // MintHighlighter
    0xFF38BDF8L, // Sky Blue
    0xFFF472B6L, // Rose Pink
    0xFFFB923CL, // Apricot
    0xFFA78BFAL  // Lavender
)

// Curated Sticky Note Card Swatches
val StickyNoteSwatches: List<Long> = listOf(
    0xFFFEF08AL, // Butter Yellow
    0xFFBBF7D0L, // Mint Sage
    0xFFBAE6FDL, // Sky Mist
    0xFFFECDD3L, // Coral Blush
    0xFFE9D5FFL, // Soft Lilac
    0xFFFED7AAL  // Warm Peach
)
