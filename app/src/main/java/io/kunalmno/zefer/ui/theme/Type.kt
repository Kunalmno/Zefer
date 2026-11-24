package io.kunalmno.zefer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.kunalmno.zefer.R

/**
 * Bundled variable fonts (res/font). One .ttf per family; weights are picked with
 * FontVariation (needs API 26+, older devices fall back to the default weight).
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { w ->
        Font(
            resId = resId,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))
        )
    }
)

val DotoFamily = variableFamily(R.font.doto, FontWeight.Normal, FontWeight.Bold)
val PixelifyFamily = variableFamily(R.font.pixelify_sans, FontWeight.Normal, FontWeight.Medium)
val MontserratFamily = variableFamily(
    R.font.montserrat,
    FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold
)

val AppTypography = Typography(
    // Doto – pixel display headings
    displayLarge = TextStyle(            // onboarding heading
        fontFamily = DotoFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp, letterSpacing = 2.sp
    ),
    headlineMedium = TextStyle(          // "Hot Deals", "New Arrivals"
        fontFamily = DotoFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp
    ),
    headlineSmall = TextStyle(           // product name
        fontFamily = DotoFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp
    ),

    // Montserrat – content
    displaySmall = TextStyle(            // price, "D.I.Y"
        fontFamily = MontserratFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp
    ),
    titleLarge = TextStyle(              // card title, product headline
        fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp
    ),
    titleMedium = TextStyle(             // "Test AR"
        fontFamily = MontserratFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp
    ),
    titleSmall = TextStyle(              // "UPTO 75%"
        fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp
    ),
    bodyMedium = TextStyle(              // product description
        fontFamily = MontserratFamily, fontWeight = FontWeight.Light, fontSize = 15.sp, lineHeight = 19.sp
    ),
    bodySmall = TextStyle(               // banner small print
        fontFamily = MontserratFamily, fontWeight = FontWeight.Light, fontSize = 13.sp, lineHeight = 16.sp
    ),
    labelMedium = TextStyle(             // "DISCOUNT"
        fontFamily = MontserratFamily, fontWeight = FontWeight.Light, fontSize = 12.sp, letterSpacing = 1.sp
    ),

    // Pixelify Sans – onboarding copy & START
    bodyLarge = TextStyle(
        fontFamily = PixelifyFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PixelifyFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp
    )
)
