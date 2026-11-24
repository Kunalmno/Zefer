package io.kunalmno.zefer.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.kunalmno.zefer.R
import io.kunalmno.zefer.ui.theme.ZeferTheme

@Composable
fun OnboardingScreen(
    onStartClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Hands / nails photo (PNG with transparent area between the fingers)
        Image(
            painter = painterResource(id = R.drawable.onboarding_nails),
            contentDescription = "Hand with decorated nail extensions",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )

        // Heading – three staggered lines
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp, start = 16.dp, end = 16.dp)
        ) {
            HeadingLine("YOUR", Modifier.padding(start = 0.dp))
            HeadingLine("NAILS", Modifier.padding(start = 52.dp))
            HeadingLine("OBSESSION", Modifier.padding(start = 82.dp))
        }

        // Description with dotted underline
        DottedUnderlineText(
            text = "Discover affordable nail extensions with virtual AR try-ons.",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 40.dp)
                .fillMaxWidth(0.6f)
        )

        // START button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onStartClick)
        ) {
            Text(
                text = "START",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeadingLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

/** Text whose every line gets a dotted underline, matching the design. */
@Composable
private fun DottedUnderlineText(text: String, modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()), 0f)
            for (i in 0 until l.lineCount) {
                val y = l.getLineBottom(i) - 2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(l.getLineLeft(i), y),
                    end = Offset(l.getLineRight(i), y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dots
                )
            }
        }
    )
}

@Preview(widthDp = 390, heightDp = 929)
@Composable
private fun OnboardingScreenPreview() {
    ZeferTheme { OnboardingScreen() }
}
