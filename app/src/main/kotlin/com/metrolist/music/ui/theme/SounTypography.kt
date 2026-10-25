/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R

private val SounFont = FontFamily(
    Font(R.font.soun_jakarta, FontWeight(400), variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.soun_jakarta, FontWeight(500), variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.soun_jakarta, FontWeight(600), variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.soun_jakarta, FontWeight(700), variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.soun_jakarta, FontWeight(800), variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

val SounTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = SounFont),
        displayMedium = base.displayMedium.copy(fontFamily = SounFont),
        displaySmall = base.displaySmall.copy(fontFamily = SounFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = SounFont, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = SounFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = SounFont),
        titleLarge = base.titleLarge.copy(fontFamily = SounFont),
        titleMedium = base.titleMedium.copy(fontFamily = SounFont),
        titleSmall = base.titleSmall.copy(fontFamily = SounFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = SounFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = SounFont),
        bodySmall = base.bodySmall.copy(fontFamily = SounFont),
        labelLarge = base.labelLarge.copy(fontFamily = SounFont),
        labelMedium = base.labelMedium.copy(fontFamily = SounFont),
        labelSmall = base.labelSmall.copy(fontFamily = SounFont),
    )
}
