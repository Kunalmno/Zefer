package io.kunalmno.zefer.data

import androidx.compose.ui.graphics.Color
import io.github.jan.supabase.postgrest.from
import io.kunalmno.zefer.data.remote.DealDto
import io.kunalmno.zefer.data.remote.ProductDto
import io.kunalmno.zefer.data.remote.SupabaseProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class Catalog(val products: List<NailSet>, val deals: List<Deal>)

/** Reads products and deals from Supabase (read-only views `product_catalog` and `deals_feed`). */
class CatalogRepository {

    suspend fun fetch(): Catalog = coroutineScope {
        val client = SupabaseProvider.client
        val productsRequest = async { client.from("product_catalog").select().decodeList<ProductDto>() }
        val dealsRequest = async { client.from("deals_feed").select().decodeList<DealDto>() }

        val products = productsRequest.await().map { it.toDomain() }.sortedBy { it.id }
        val byId = products.associateBy { it.id }
        val now = System.currentTimeMillis()

        val deals = dealsRequest.await()
            .mapNotNull { dto -> byId[dto.productId]?.let { dto.toDomain(it, now) } }
            .sortedWith(compareBy<Deal> { it.status != DealStatus.Live }.thenBy { it.secondsUntilStart })

        Catalog(products, deals)
    }
}

private fun ProductDto.toDomain(): NailSet {
    val ordered = nails.sortedBy { it.position }
    val card = cardNails.takeIf { it.size == 3 } ?: listOf(3, 5, 4)
    return NailSet(
        id = id,
        slug = slug,
        name = name,
        abbreviation = abbreviation,
        headline = headline,
        description = description,
        currencySymbol = currencySymbol(currency),
        basePrice = basePrice,
        finalPrice = finalPrice,
        discountPercent = discountPercent,
        discountEndsInSeconds = discountEndsInSeconds,
        accentColor = parseColor(accentColor),
        previewUrl = SupabaseProvider.imageUrl(previewPath),
        previewBackdrop = parseColor(previewBackdrop),
        nailUrls = ordered.map { SupabaseProvider.imageUrl(it.imagePath) },
        nailLabels = ordered.map { it.label },
        cardNails = Triple(card[0], card[1], card[2])
    )
}

private fun DealDto.toDomain(set: NailSet, fetchedAtMillis: Long) = Deal(
    id = dealId,
    set = set,
    discountPercent = discountPercent,
    status = if (status == "live") DealStatus.Live else DealStatus.Upcoming,
    dealPrice = dealPrice,
    secondsUntilStart = secondsUntilStart,
    secondsUntilEnd = secondsUntilEnd,
    fetchedAtMillis = fetchedAtMillis
)

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
