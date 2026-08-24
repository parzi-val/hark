package com.hark.ui.theme

import androidx.compose.ui.graphics.Color

// ── Hark palette, ported 1:1 from the prototype ──────────────────────────────
// Light: warm paper + ink + one rust accent.
val PaperLight = Color(0xFFF4F2ED)
val InkLight = Color(0xFF1C1B19)
val RustLight = Color(0xFF8A4B34)

// Dark: pale grey glass, same bones.
val PaperDark = Color(0xFF33342F)
val InkDark = Color(0xFFECEAE4)
val RustDark = Color(0xFFD99F83)

/** Semantic colors resolved per theme; alpha variants are derived in [HarkColors]. */
class HarkColors(
    val paper: Color,   // background
    val ink: Color,     // primary text / marks
    val rust: Color,    // accent
    val isDark: Boolean,
) {
    // Muted inks — the prototype leans on ink-at-alpha for hierarchy.
    val inkMuted: Color get() = ink.copy(alpha = if (isDark) 0.60f else 0.55f)
    val inkFaint: Color get() = ink.copy(alpha = if (isDark) 0.42f else 0.40f)
    val inkHairline: Color get() = ink.copy(alpha = if (isDark) 0.12f else 0.09f)
    val checkboxBorder: Color get() = ink.copy(alpha = if (isDark) 0.45f else 0.42f)
    val paperRaised: Color get() = if (isDark) ink.copy(alpha = 0.04f) else Color(0xFFFAF8F4)
}

val LightHarkColors = HarkColors(PaperLight, InkLight, RustLight, isDark = false)
val DarkHarkColors = HarkColors(PaperDark, InkDark, RustDark, isDark = true)
