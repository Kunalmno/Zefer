package io.kunalmno.zefer.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** A landmark. x/y are 0..1 inside the upright camera frame, in the same orientation as the preview. */
data class Pt(val x: Float, val y: Float, val z: Float)

data class TrackedHand(
    /** "Left" / "Right" as reported by MediaPipe. */
    val label: String,
    val score: Float,
    /** The user's real left hand. */
    val isLeft: Boolean,
    /** The BACK of the hand faces the camera (so the nails are visible). */
    val isBackOfHand: Boolean,
    /** 21 normalized landmarks (0 wrist, 4/8/12/16/20 fingertips). */
    val points: List<Pt>,
    /** 21 landmarks in metres, origin at the hand centre – used for tilt in a later stage. */
    val world: List<Pt>
)

data class HandFrame(
    val hands: List<TrackedHand> = emptyList(),
    /** Size of the upright frame the landmarks refer to. */
    val width: Int = 0,
    val height: Int = 0
)

/**
 * Wraps MediaPipe's Hand Landmarker (runs fully on the phone).
 * Feed it camera frames with [analyze]; read results from [frame].
 *
 * Every frame is mirrored before MediaPipe sees it (selfie view). That is the view MediaPipe's
 * Left/Right labels are defined for, so labels are the user's real hands on BOTH cameras.
 * For the rear camera the landmarks are flipped back so they line up with the (unmirrored) preview.
 */
class HandTracker(context: Context, preferGpu: Boolean = false) : AutoCloseable {

    private val lock = Any()
    @Volatile private var closed = false
    @Volatile private var flipBack = false
    private var lastTimestamp = 0L

    /** True while MediaPipe is busy with a frame. New camera frames are dropped meanwhile, so results never queue up. */
    private val inFlight = AtomicBoolean(false)
    @Volatile private var submittedAt = 0L

    private val _frame = MutableStateFlow(HandFrame())
    val frame: StateFlow<HandFrame> = _frame.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    /** Milliseconds from handing a camera frame to MediaPipe until its result arrives. */
    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    private var fpsCount = 0
    private var fpsWindowStart = SystemClock.uptimeMillis()

    /** "GPU" or "CPU" – whichever delegate is actually running. */
    val delegateName: String

    private val landmarker: HandLandmarker

    init {
        var name = if (preferGpu) "GPU" else "CPU"
        landmarker = if (preferGpu) {
            try {
                build(context, Delegate.GPU)
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate failed, falling back to CPU", e)
                name = "CPU"
                build(context, Delegate.CPU)
            }
        } else {
            build(context, Delegate.CPU)
        }
        delegateName = name
    }

    private fun build(context: Context, delegate: Delegate): HandLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.4f)
            .setMinHandPresenceConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .setResultListener { result, image -> onResult(result, image) }
            .setErrorListener { e -> Log.e(TAG, "MediaPipe error", e); inFlight.set(false) }
            .build()
        return HandLandmarker.createFromOptions(context, options)
    }

    /** Call from the CameraX analyzer thread. Closes the [proxy] itself. */
    fun analyze(proxy: ImageProxy, isFrontCamera: Boolean) {
        var submitted = false
        var acquired = false
        try {
            if (closed) return
            // Busy? Drop this frame instead of queueing it (a stuck flag is released after 1 s as a safety net).
            if (!inFlight.compareAndSet(false, true)) {
                if (SystemClock.uptimeMillis() - submittedAt < 1000) return
            }
            acquired = true
            submittedAt = SystemClock.uptimeMillis()
            val source = proxy.toBitmap()
            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)                       // selfie view for MediaPipe, always
            }
            val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val image = BitmapImageBuilder(upright).build()

            synchronized(lock) {
                if (closed) return
                flipBack = !isFrontCamera
                val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1)
                lastTimestamp = timestamp
                landmarker.detectAsync(image, timestamp)
                submitted = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "analyze failed", e)
        } finally {
            if (acquired && !submitted) inFlight.set(false)
            proxy.close()
        }
    }

    private fun onResult(result: HandLandmarkerResult, image: MPImage) {
        inFlight.set(false)
        val flip = flipBack
        val hands = result.landmarks().indices.map { i ->
            val category = result.handednesses().getOrNull(i)?.firstOrNull()
            val label = category?.categoryName() ?: "?"
            val raw = result.landmarks()[i].map { Pt(it.x(), it.y(), it.z()) }

            // Which way is the hand facing? The sign of (wrist->index knuckle) x (wrist->pinky knuckle),
            // combined with the left/right label, tells palm from back (checked on real phone frames).
            val cross = (raw[5].x - raw[0].x) * (raw[17].y - raw[0].y) -
                (raw[5].y - raw[0].y) * (raw[17].x - raw[0].x)
            val isLeft = label == "Left"
            val isBack = (isLeft && cross < 0f) || (!isLeft && cross > 0f)

            TrackedHand(
                label = label,
                score = category?.score() ?: 0f,
                isLeft = isLeft,
                isBackOfHand = isBack,
                points = if (flip) raw.map { it.copy(x = 1f - it.x) } else raw,
                world = result.worldLandmarks().getOrNull(i)
                    // World coordinates stay in the mirrored frame on BOTH cameras: they are only used for tilt, and the
                    // left/right label + facing maths are defined for that frame (flipping x here hid every nail on the rear camera).
                    ?.map { Pt(it.x(), it.y(), it.z()) } ?: emptyList()
            )
        }
        _frame.value = HandFrame(hands, image.width, image.height)

        val now = SystemClock.uptimeMillis()
        _latencyMs.value = (now - result.timestampMs()).toInt().coerceAtLeast(0)
        fpsCount++
        if (now - fpsWindowStart >= 1000) {
            _fps.value = fpsCount
            fpsCount = 0
            fpsWindowStart = now
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            landmarker.close()
        }
    }

    companion object {
        private const val TAG = "HandTracker"
        const val MODEL_ASSET = "hand_landmarker.task"
    }
}
