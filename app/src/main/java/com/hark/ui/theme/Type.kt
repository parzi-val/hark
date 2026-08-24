package com.hark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hark.R

// Bundled prototype fonts: Libre Baskerville (variable, serif) + Syne Mono (accent/labels).
val HarkSerif: FontFamily = FontFamily(
    Font(R.font.libre_baskerville, FontWeight.Normal),
    Font(R.font.libre_baskerville_italic, FontWeight.Normal, FontStyle.Italic),
)
val HarkMono: FontFamily = FontFamily(Font(R.font.syne_mono, FontWeight.Normal))

/** Named styles used directly by Hark components (mono labels are meant to be UPPERCASE). */
object HarkType {
    val displayNumber = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 54.sp, lineHeight = 54.sp)
    val title = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp)
    val noteTitle = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 34.sp)
    val item = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp)
    val bodyRelaxed = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 30.sp)
    val secondary = TextStyle(fontFamily = HarkSerif, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = HarkMono, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, letterSpacing = 0.16.em)
    val meta = TextStyle(fontFamily = HarkMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.16.em)
}

val Typography = Typography(
    bodyLarge = HarkType.body,
    titleLarge = HarkType.title,
    labelSmall = HarkType.label,
)
