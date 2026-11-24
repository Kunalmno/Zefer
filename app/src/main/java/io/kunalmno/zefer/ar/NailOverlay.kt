package io.kunalmno.zefer.ar

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the smoother's nails onto any Android canvas – the live overlay and the captured photo
 * use the very same code, so the photo looks exactly like the screen.
 */
class NailPainter {
    private val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val matrix = Matrix()
    private val src = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)   // TL, TR, BR, BL of the bitmap
    private val dst = FloatArray(8)

    fun draw(canvas: android.graphics.Canvas, smoother: NailSmoother, nails: List<Bitmap?>) {
        smoother.forEachVisible { index, cx0, cy0, angle, w, h, alpha, fl, fw, uz ->
            val bmp = nails.getOrNull(index) ?: return@forEachVisible
            // Nail as a quad in its own frame (x across, y along the finger, cuticle at the top):
            // shorter when the finger points at the camera, narrower when rolled, and the free edge
            // (bottom) slightly narrower when it is further away than the cuticle.
            val halfTop = w * fw / 2f
            val halfBottom = halfTop * (1f - NailTuning.TAPER * uz).coerceIn(0.7f, 1.3f)
            val halfH = h * fl / 2f
            val rad = Math.toRadians(angle.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            // Foreshortening shrinks the nail towards its CUTICLE (which stays on the finger), not around its middle.
            val shrink = h / 2f * (1f - fl)
            val cx = cx0 + s * shrink
            val cy = cy0 - c * shrink
            fun put(i: Int, x: Float, y: Float) {          // rotate clockwise by angle, move to centre
                dst[i * 2] = cx + x * c - y * s
                dst[i * 2 + 1] = cy + x * s + y * c
            }
            put(0, -halfTop, -halfH); put(1, halfTop, -halfH)
            put(2, halfBottom, halfH); put(3, -halfBottom, halfH)
            src[2] = bmp.width.toFloat(); src[4] = bmp.width.toFloat()
            src[5] = bmp.height.toFloat(); src[7] = bmp.height.toFloat()
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp, matrix, paint)
        }
    }
}

/**
 * Live nails on the tracked fingers.
 * Reads the tracking result inside the frame loop / draw phase, so only this canvas redraws – not the whole screen.
 */
@Composable
fun NailOverlay(
    smoother: NailSmoother,
    frameState: State<HandFrame>,
    latencyState: State<Int>,
    nails: List<Bitmap?>,
    showAlways: Boolean,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableLongStateOf(0L) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val latestNails by rememberUpdatedState(nails)
    val latestShowAlways by rememberUpdatedState(showAlways)
    val painter = remember { NailPainter() }

    // Runs once per display frame (~60 Hz): pick up new tracking results, then animate toward them.
    LaunchedEffect(smoother) {
        var lastFrame: HandFrame? = null
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                smoother.latencyMs = latencyState.value
                val frame = frameState.value
                if (frame !== lastFrame && viewSize.width > 0) {
                    smoother.setTargets(frame, viewSize.width.toFloat(), viewSize.height.toFloat(), latestNails, latestShowAlways)
                    lastFrame = frame
                }
                val dt = if (lastNanos == 0L) 16f else ((now - lastNanos) / 1_000_000f).coerceAtMost(100f)
                lastNanos = now
                smoother.step(dt)
                tick = now
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        @Suppress("UNUSED_VARIABLE") val redrawEveryFrame = tick
        drawIntoCanvas { canvas -> painter.draw(canvas.nativeCanvas, smoother, nails) }
    }
}
