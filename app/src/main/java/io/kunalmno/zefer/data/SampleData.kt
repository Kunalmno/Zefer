package io.kunalmno.zefer.data

import androidx.compose.ui.graphics.Color

/** Fake data for @Preview only – the real app loads everything from Supabase. */
object SampleData {
    val sets: List<NailSet> = listOf(
        sample(1, "Fairy Wing Garden", "FWG", 199.0, 149.0, 25, 0xFFC9E371),
        sample(2, "Rose Shell Luxe", "RSL", 249.0, 249.0, 0, 0xFFD79586),
        sample(3, "Midnight Cosmos", "MC", 229.0, 229.0, 0, 0xFFA2A27A)
    )

    val deals: List<Deal> = listOf(
        Deal(1, sets[0], 25, DealStatus.Live, 149.0, 0, 4 * 86_400L, 0L),
        Deal(2, sets[2], 30, DealStatus.Upcoming, 160.0, 5 * 86_400L, 12 * 86_400L, 0L)
    )

    private fun sample(id: Long, name: String, code: String, base: Double, final: Double, off: Int, accent: Long) = NailSet(
        id = id, slug = name.lowercase().replace(' ', '-'), name = name, abbreviation = code,
        headline = "$name Set, 10 Nails Pack",
        description = "Sample description for previews. The real text comes from Supabase.",
        currencySymbol = "₹", basePrice = base, finalPrice = final, discountPercent = off,
        discountEndsInSeconds = if (off > 0) 4 * 86_400L else null,
        accentColor = Color(accent), previewUrl = "", previewBackdrop = Color(0xFFD2CBCA),
        nailUrls = List(10) { "" },
        nailLabels = listOf(
            "Left pinky", "Left ring", "Left middle", "Left index", "Left thumb",
            "Right thumb", "Right index", "Right middle", "Right ring", "Right pinky"
        ),
        cardNails = Triple(3, 5, 4)
    )
}
