package com.omargarcia.blocky.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.omargarcia.blocky.R

val VT323Font = FontFamily(
    Font(R.font.vt323, FontWeight.Normal)
)

val SilkscreenFont = FontFamily(
    Font(R.font.silkscreen, FontWeight.Normal)
)

val PressStart2PFont = FontFamily(
    Font(R.font.press_start_2p, FontWeight.Normal)
)

val PixelifySansFont = FontFamily(
    Font(R.font.pixelify_sans, FontWeight.Normal),
    Font(R.font.pixelify_sans, FontWeight.Medium),
    Font(R.font.pixelify_sans, FontWeight.SemiBold),
    Font(R.font.pixelify_sans, FontWeight.Bold)
)

val PixelTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 60.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 50.sp,
        lineHeight = 54.sp
    ),
    displaySmall = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = VT323Font,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)