package io.kunalmno.zefer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZeferColorScheme = lightColorScheme(
    primary = Color.Black,               // START, Test AR, heart buttons
    onPrimary = Beige,
    secondaryContainer = BannerTan,      // deal banner
    onSecondaryContainer = Ink,
    tertiary = PriceGreen,               // prices
    background = Beige,
    onBackground = Ink,
    surface = CardBeige,                 // product cards
    onSurface = Ink,
    onSurfaceVariant = DescriptionGrey,  // product description
    surfaceContainer = BottomBarBeige    // product bottom bar
)

@Composable
fun ZeferTheme(content: @Composable () -> Unit) {
    // Light only: the design is a fixed beige palette.
    MaterialTheme(
        colorScheme = ZeferColorScheme,
        typography = AppTypography,
        content = content
    )
}
