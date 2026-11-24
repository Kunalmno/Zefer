package io.kunalmno.zefer.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of the `product_catalog` view. */
@Serializable
data class ProductDto(
    val id: Long,
    val slug: String,
    val name: String,
    val abbreviation: String,
    val headline: String,
    val description: String,
    val currency: String = "INR",
    @SerialName("accent_color") val accentColor: String,
    @SerialName("preview_path") val previewPath: String,
    @SerialName("preview_backdrop") val previewBackdrop: String,
    @SerialName("card_nails") val cardNails: List<Int> = listOf(3, 5, 4),
    @SerialName("base_price") val basePrice: Double,
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("final_price") val finalPrice: Double,
    @SerialName("discount_ends_in_seconds") val discountEndsInSeconds: Long? = null,
    val nails: List<NailDto> = emptyList()
)

@Serializable
data class NailDto(
    val position: Int,
    val label: String,
    @SerialName("image_path") val imagePath: String
)

/** One row of the `deals_feed` view. */
@Serializable
data class DealDto(
    @SerialName("deal_id") val dealId: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("discount_percent") val discountPercent: Int,
    val status: String,
    @SerialName("deal_price") val dealPrice: Double,
    @SerialName("seconds_until_start") val secondsUntilStart: Long,
    @SerialName("seconds_until_end") val secondsUntilEnd: Long
)
