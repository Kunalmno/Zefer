package io.kunalmno.zefer.ui.product

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.kunalmno.zefer.data.NailSet
import io.kunalmno.zefer.data.SampleData
import io.kunalmno.zefer.ui.theme.RingGold
import io.kunalmno.zefer.ui.theme.SpotShadow
import io.kunalmno.zefer.ui.theme.ZeferTheme
import io.kunalmno.zefer.util.inText
import kotlin.math.abs

@Composable
fun ProductScreen(
    product: NailSet,
    isFavorite: Boolean = false,
    onBackClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onTestArClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Start on the left pinky (nail 1); change initialPage to start elsewhere.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { product.nailUrls.size.coerceAtLeast(1) })
    var showViewer by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp)) {
            TopBar(isFavorite, onBackClick, onShareClick, onFavoriteClick)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                NailCarousel(product, pagerState)

                Text(
                    text = "${product.nailLabels.getOrElse(pagerState.currentPage) { "" }}  ·  ${pagerState.currentPage + 1}/${product.nailUrls.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Spacer(Modifier.height(16.dp))
                PreviewBox(
                    product = product,
                    onClick = { showViewer = true },
                    modifier = Modifier.padding(horizontal = 27.dp)
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = product.headline,
                    style = MaterialTheme.typography.titleLarge.copy(lineHeight = 34.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 27.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(horizontal = 27.dp)
                )

                // room for the bottom bar + floating Test AR button
                Spacer(Modifier.height(190.dp))
            }
        }

        BottomBar(
            product = product,
            onCartClick = onCartClick,
            onTestArClick = onTestArClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showViewer) {
        ImageViewerDialog(
            imageUrl = product.previewUrl,
            contentDescription = "${product.name} – all nails",
            onDismiss = { showViewer = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------
@Composable
private fun TopBar(
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = ink,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBackClick)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = ink,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onShareClick)
            )
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favourite",
                tint = ink,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onFavoriteClick)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Carousel: one nail per page on the ring light, in real-hand order
// (left pinky -> left thumb, right thumb -> right pinky)
// ---------------------------------------------------------------------------
@Composable
private fun NailCarousel(product: NailSet, pagerState: PagerState) {
    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(214.dp),
        contentPadding = PaddingValues(horizontal = 88.dp),
        pageSpacing = 22.dp,
        modifier = Modifier.fillMaxWidth().height(320.dp)
    ) { page ->
        val offset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f - 0.08f * offset
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.15f * offset
                }
        ) {
            Spotlight(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
            AsyncImage(
                model = product.nailUrls.getOrNull(page),
                contentDescription = product.nailLabels.getOrNull(page),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .size(width = 124.dp, height = 228.dp)
            )
        }
    }
}

/** Soft floor shadow + thin gold ring (from spotlight.svg, 214 x 135). */
@Composable
private fun Spotlight(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier.size(width = 214.dp, height = 135.dp)) {
        val w = size.width
        val h = size.height

        val cx = w * 0.5f
        val cy = h * 0.52f
        val rx = w * 0.34f
        val ry = h * 0.30f
        scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SpotShadow, SpotShadow.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = rx * 1.35f
                ),
                radius = rx * 1.35f,
                center = Offset(cx, cy)
            )
        }

        val strokeW = 3.dp.toPx()
        drawOval(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(0.28f to background, 1f to RingGold),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset(strokeW / 2, h * 0.068f + strokeW / 2),
            size = Size(w - strokeW, h * 0.932f - strokeW),
            style = Stroke(width = strokeW)
        )
    }
}

// ---------------------------------------------------------------------------
// Box showing the whole set on its background – tap to open full screen
// ---------------------------------------------------------------------------
@Composable
private fun PreviewBox(product: NailSet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(product.previewBackdrop)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = product.previewUrl,
            contentDescription = "${product.name} – all nails. Tap to view full screen",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Fullscreen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Tap to view", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom bar with cart, price and the floating "Test AR" button
// ---------------------------------------------------------------------------
@Composable
private fun BottomBar(
    product: NailSet,
    onCartClick: () -> Unit,
    onTestArClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .height(99.dp)
                .padding(start = 26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ShoppingCart,
                contentDescription = "Add to cart",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onCartClick)
            )
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = product.priceText,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (product.hasDiscount) {
                        Text(
                            text = product.basePriceText,
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                        )
                    }
                }
                if (product.hasDiscount) {
                    val ends = product.discountEndsInSeconds?.let { " · ends ${inText(it)}" } ?: ""
                    Text(
                        text = "${product.discountPercent}% off$ends",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 15.dp, bottom = 24.dp)
                .size(114.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onTestArClick)
        ) {
            Text(
                text = "Test AR",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 929)
@Composable
private fun ProductScreenPreview() {
    ZeferTheme { ProductScreen(product = SampleData.sets.first()) }
}
