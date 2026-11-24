package io.kunalmno.zefer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.kunalmno.zefer.data.Deal
import io.kunalmno.zefer.data.DealStatus
import io.kunalmno.zefer.data.NailSet
import io.kunalmno.zefer.data.SampleData
import io.kunalmno.zefer.ui.theme.Beige
import io.kunalmno.zefer.ui.theme.Ink
import io.kunalmno.zefer.ui.theme.ZeferTheme
import io.kunalmno.zefer.util.dateLabel
import io.kunalmno.zefer.util.inText
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun HomeScreen(
    products: List<NailSet>,
    deals: List<Deal>,
    favorites: List<Long> = emptyList(),
    onMenuClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onDealClick: (Deal) -> Unit = {},
    onProductClick: (NailSet) -> Unit = {},
    onFavoriteClick: (NailSet) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 12.dp)
    ) {
        TopBar(onMenuClick, onCartClick)

        if (deals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("Hot Deals", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            DealsCarousel(deals = deals, onDealClick = onDealClick)
            Spacer(Modifier.height(40.dp))
        } else {
            Spacer(Modifier.height(48.dp))
        }

        SectionTitle("New Arrivals", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(24.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(25.dp)
        ) {
            items(products, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    isFavorite = product.id in favorites,
                    onClick = { onProductClick(product) },
                    onFavoriteClick = { onFavoriteClick(product) }
                )
            }
        }
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

@Composable
private fun TopBar(onMenuClick: () -> Unit, onCartClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Custom "hamburger" with the little dashes from the design
        Canvas(
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onMenuClick)
        ) {
            val stroke = 2.dp.toPx()
            listOf(size.height * 0.38f, size.height * 0.62f).forEach { y ->
                drawLine(ink, Offset(0f, y), Offset(3.dp.toPx(), y), stroke, StrokeCap.Round)
                drawLine(ink, Offset(7.dp.toPx(), y), Offset(size.width, y), stroke, StrokeCap.Round)
            }
        }
        Icon(
            imageVector = Icons.Outlined.ShoppingCart,
            contentDescription = "Cart",
            tint = ink,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onCartClick)
        )
    }
}

/** Doto title with dotted underline. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = ink,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()), 0f)
            val y = l.getLineBottom(0) - 1.dp.toPx()
            drawLine(ink, Offset(l.getLineLeft(0), y), Offset(l.getLineRight(0), y), 1.5.dp.toPx(), pathEffect = dots)
        }
    )
}

// ---------------------------------------------------------------------------
// Hot Deals – auto-rolling carousel, one coloured card per deal
// ---------------------------------------------------------------------------
@Composable
private fun DealsCarousel(
    deals: List<Deal>,
    onDealClick: (Deal) -> Unit,
    modifier: Modifier = Modifier
) {
    if (deals.isEmpty()) return
    val count = deals.size
    val loops = 1000
    val pagerState = rememberPagerState(
        initialPage = count * (loops / 2),   // start in the middle so you can swipe both ways
        pageCount = { count * loops }
    )

    // Roll to the next deal every few seconds (skipped while the user is dragging).
    LaunchedEffect(pagerState, count) {
        if (count < 2) return@LaunchedEffect
        while (true) {
            delay(3500)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp
        ) { page ->
            val deal = deals[page % count]
            DealCard(deal = deal, onClick = { onDealClick(deal) })
        }

        if (count > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val current = pagerState.currentPage % count
                repeat(count) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == current) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onBackground.copy(alpha = if (i == current) 0.9f else 0.3f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun DealCard(deal: Deal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val set = deal.set
    val background = set.accentColor
    val ink = readableOn(background)
    val (left, center, right) = set.cardNails
    val isLive = deal.status == DealStatus.Live
    val seconds = if (isLive) deal.secondsUntilEnd else deal.secondsUntilStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
    ) {
        // Three nails from the set, fanned out
        CardNail(set.nailUrl(left), Modifier.offset(x = 18.dp, y = 26.dp).graphicsLayer { rotationZ = -10f }, height = 118.dp)
        CardNail(set.nailUrl(right), Modifier.offset(x = 116.dp, y = 26.dp).graphicsLayer { rotationZ = 10f }, height = 118.dp)
        CardNail(set.nailUrl(center), Modifier.offset(x = 62.dp, y = 8.dp), height = 148.dp, description = set.name)

        Text("${deal.discountPercent}% OFF", style = type.titleSmall, color = ink,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp))
        Text(if (isLive) "LIVE NOW" else "COMING SOON", style = type.labelMedium, color = ink,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 29.dp, end = 10.dp))
        Text(set.abbreviation, style = type.displaySmall, color = ink,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 222.dp, top = 8.dp))
        Text(
            text = buildAnnotatedString {
                append(if (isLive) "ENDS\n" else "STARTS\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(dateLabel(deal.fetchedAtMillis, seconds))
                    append("\n")
                    append(inText(seconds).uppercase())
                }
            },
            style = type.bodySmall,
            color = ink,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 264.dp, bottom = 10.dp)
        )
    }
}

/** Dark ink or light beige, whichever is easier to read on [background]. */
private fun readableOn(background: Color): Color {
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
    return if (contrast(background, Ink) >= contrast(background, Beige)) Ink else Beige
}

// ---------------------------------------------------------------------------
// New Arrivals
// ---------------------------------------------------------------------------
@Composable
private fun ProductCard(
    product: NailSet,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (left, center, right) = product.cardNails

    Box(
        modifier = modifier
            .width(302.dp)
            .height(353.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        // Three overlapping nails from the set (centre one on top)
        CardNail(product.nailUrl(left), Modifier.offset(x = 40.dp, y = 48.dp), height = 200.dp)
        CardNail(product.nailUrl(right), Modifier.offset(x = 138.dp, y = 40.dp), height = 208.dp)
        CardNail(product.nailUrl(center), Modifier.offset(x = 84.dp, y = 22.dp), height = 236.dp, description = product.name)

        // Favourite button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(18.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onFavoriteClick)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favourite",
                tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Discount badge while a deal is live
        if (product.hasDiscount) {
            Text(
                text = "${product.discountPercent}% OFF",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 22.dp)
        ) {
            Text(product.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 14.dp)
            ) {
                Text(
                    text = product.priceText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (product.hasDiscount) {
                    Text(
                        text = product.basePriceText,
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardNail(
    url: String?,
    modifier: Modifier,
    height: Dp,
    description: String? = null
) {
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(width = height * 0.56f, height = height)
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    ZeferTheme { HomeScreen(products = SampleData.sets, deals = SampleData.deals) }
}
