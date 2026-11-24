package io.kunalmno.zefer.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

object ArConfig {
    /** Shows the fps / delegate line and the debug chips on the AR screen. */
    const val SHOW_DEBUG_SKELETON = true
}

enum class HandStatus(val message: String) {
    NoHand("Show the back of your hand"),
    OutOfFrame("Move your hand fully into the frame"),
    TooFar("Move your hand closer"),
    TooClose("Move your hand back a little"),
    Ready("Hand detected")
}

/** Decides what hint to show, from the biggest / most confident hand in view. */
fun assessHands(frame: HandFrame): HandStatus {
    val hand = frame.hands.maxByOrNull { it.score } ?: return HandStatus.NoHand
    val xs = hand.points.map { it.x }
    val ys = hand.points.map { it.y }
    if (xs.min() < 0.01f || xs.max() > 0.99f || ys.min() < 0.01f || ys.max() > 0.99f) return HandStatus.OutOfFrame
    val size = max(xs.max() - xs.min(), ys.max() - ys.min())
    return when {
        size < 0.25f -> HandStatus.TooFar
        size > 0.90f -> HandStatus.TooClose
        else -> HandStatus.Ready
    }
}

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20,
    0 to 17
)
private val FINGERTIPS = setOf(4, 8, 12, 16, 20)

/**
 * Maps a normalized landmark to screen pixels, matching PreviewView's FILL_CENTER
 * (the camera frame is scaled to cover the view and the overflow is cropped).
 */
class FrameMapper(frameW: Int, frameH: Int, viewW: Float, viewH: Float) {
    private val scale = if (frameW > 0 && frameH > 0) max(viewW / frameW, viewH / frameH) else 1f
    private val dx = (viewW - frameW * scale) / 2f
    private val dy = (viewH - frameH * scale) / 2f
    private val fw = frameW.toFloat()
    private val fh = frameH.toFloat()

    fun map(p: Pt) = Offset(p.x * fw * scale + dx, p.y * fh * scale + dy)
}

/** Camera guide brackets + (debug) hand skeleton, drawn over the preview. */
@Composable
fun HandOverlay(frame: HandFrame, status: HandStatus, showSkeleton: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawGuideFrame(if (status == HandStatus.Ready) Color(0xFF7DFF8A) else Color.White)

        if (showSkeleton && frame.width > 0) {
            val mapper = FrameMapper(frame.width, frame.height, size.width, size.height)
            frame.hands.forEach { hand ->
                val pts = hand.points.map(mapper::map)
                HAND_CONNECTIONS.forEach { (a, b) ->
                    if (a < pts.size && b < pts.size) {
                        drawLine(Color.White.copy(alpha = 0.7f), pts[a], pts[b], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                pts.forEachIndexed { i, p ->
                    val tip = i in FINGERTIPS
                    drawCircle(
                        color = if (tip) Color(0xFFFFC857) else Color(0xFF7DFF8A),
                        radius = (if (tip) 6.dp else 3.5.dp).toPx(),
                        center = p
                    )
                }
            }
        }
    }
}

/** Four corner brackets in the middle of the screen, like a camera viewfinder. */
private fun DrawScope.drawGuideFrame(color: Color) {
    val w = size.width * 0.84f
    val h = size.height * 0.52f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val arm = 34.dp.toPx()
    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    val r = CornerRadius(18.dp.toPx())

    fun corner(x: Float, y: Float, sx: Float, sy: Float) {
        // two arms meeting at (x, y); sx/sy = direction of the arms (+1 or -1)
        drawLine(color, Offset(x, y), Offset(x + sx * arm, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(x, y), Offset(x, y + sy * arm), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
    corner(left, top, 1f, 1f)
    corner(left + w, top, -1f, 1f)
    corner(left, top + h, 1f, -1f)
    corner(left + w, top + h, -1f, -1f)
    // faint full outline so the frame reads as one shape
    drawRoundRect(color.copy(alpha = 0.18f), Offset(left, top), Size(w, h), r, style = Stroke(1.5.dp.toPx()))
}
