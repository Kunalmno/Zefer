package io.kunalmno.zefer.data

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * One press-on set loaded from Supabase: 10 nails ordered like a real pair of hands
 * (left pinky -> left thumb, then right thumb -> right pinky) plus a photo of the whole set.
 */
data class NailSet(
    val id: Long,
    val slug: String,
    val name: String,
    /** Short code shown on the Hot Deals card, e.g. "FWG". */
    val abbreviation: String,
    val headline: String,
    val description: String,
    val currencySymbol: String,
    val basePrice: Double,
    /** Price right now (base price minus the live deal, if any). */
    val finalPrice: Double,
    val discountPercent: Int,
    /** Seconds until the live deal ends (null when no deal is live). */
    val discountEndsInSeconds: Long?,
    /** Background colour of this set's card in the Hot Deals carousel. */
    val accentColor: Color,
    val previewUrl: String,
    val previewBackdrop: Color,
    val nailUrls: List<String>,
    val nailLabels: List<String>,
    /** 1-based nail numbers (left, centre, right) shown on cards. */
    val cardNails: Triple<Int, Int, Int>
) {
    val hasDiscount: Boolean get() = discountPercent > 0 && finalPrice < basePrice
    val priceText: String get() = formatPrice(currencySymbol, finalPrice)
    val basePriceText: String get() = formatPrice(currencySymbol, basePrice)

    /** Nail image URL for a 1-based nail number (safe if the set has fewer nails). */
    fun nailUrl(number: Int): String? = nailUrls.getOrNull(number - 1)
}

enum class DealStatus { Live, Upcoming }

/** A time-boxed discount on one [NailSet]. Seconds are relative to [fetchedAtMillis]. */
data class Deal(
    val id: Long,
    val set: NailSet,
    val discountPercent: Int,
    val status: DealStatus,
    val dealPrice: Double,
    val secondsUntilStart: Long,
    val secondsUntilEnd: Long,
    val fetchedAtMillis: Long
) {
    val dealPriceText: String get() = formatPrice(set.currencySymbol, dealPrice)
}

fun currencySymbol(code: String): String = when (code.trim().uppercase(Locale.ROOT)) {
    "INR" -> "₹"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> code.trim() + " "
}

fun formatPrice(symbol: String, value: Double): String =
    if (value % 1.0 == 0.0) "$symbol${value.toLong()}" else "$symbol${"%.2f".format(Locale.US, value)}"
