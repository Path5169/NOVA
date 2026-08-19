package com.nova.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System default families kept deliberately (monospace for data readouts,
// sans for everything else) so the app builds with zero bundled font assets.
// Swap FontFamily.Default for a bundled display face later without touching call sites.
val NovaDisplayFont = FontFamily.SansSerif
val NovaMonoFont = FontFamily.Monospace

val NovaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NovaDisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NovaMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NovaMonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 1.sp
    )
)

// Dedicated style for large numeric readouts (sensor values, metrics).
// Not part of Material's Typography slots, so exposed as a standalone token.
val NovaReadoutStyle = TextStyle(
    fontFamily = NovaMonoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    letterSpacing = 0.sp
)
