package io.kunalmno.zefer.ar

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** Numbers tuned on real hand photos (see the placement test in the chat). */
object NailTuning {
    /** The nail's cuticle (base) sits this fraction of the finger's last segment below the fingertip. (live-tunable) */
    @Volatile var baseFrac = 0.75f

    /** Multiplies every nail's size. (live-tunable) */
    @Volatile var sizeScale = 1.0f

    /** Push nails ahead along the hand's motion to hide the tracking delay. (live-tunable) */
    @Volatile var predict = true

    /** Degrees added to the finger direction. 180 = artwork is drawn cuticle-at-top, free-edge-at-bottom. */
    const val ART_ROTATION = 180f

    /** Use the 3D landmarks to tilt nails (foreshortening, narrowing, taper, fade when curled). */
    const val USE_TILT = true

    /** Perspective taper: how much narrower the free edge gets when the fingertip points away from the camera. */
    const val TAPER = 0.18f

    /** Facing (1 = nail faces the camera, 0 = edge-on, <0 = faces away): fully visible above HIGH, hidden below LOW. */
    const val FADE_LOW = 0.10f
    const val FADE_HIGH = 0.38f
}

/** Finger -> (landmark of the last joint, landmark of the tip, nail width as a share of knuckle spacing). */
/** [baseBias] is added to the Base setting for this finger (small hand-tuned corrections: pinky and thumb floated most). */
internal enum class Finger(val dip: Int, val tip: Int, val widthFactor: Float, val baseBias: Float) {
    Pinky(19, 20, 0.66f, 0.05f),
    Ring(15, 16, 0.74f, 0f),
    Middle(11, 12, 0.78f, 0f),
    Index(7, 8, 0.78f, 0f),
    Thumb(3, 4, 0.80f, 0.12f)
}

private data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(x * x + y * y + z * z)
    fun unit(): V3 { val l = len(); return if (l < 1e-6f) this else V3(x / l, y / l, z / l) }
}

private fun Pt.v3() = V3(x, y, z)

/** Tilt of one nail, from the 3D landmarks. */
private class Tilt(val fl: Float, val fw: Float, val uz: Float, val facing: Float) {
    companion object { val FLAT = Tilt(1f, 1f, 0f, 1f) }
}

/**
 * Turns hand landmarks into one smoothed pose per nail and animates them at the display refresh rate,
 * so the nails glide even though hand tracking only delivers a few results per second.
 *
 * Nail index: 0..4 = left pinky..thumb, 5..9 = right thumb..pinky (same order as the images).
 */
class NailSmoother {

    private class State {
        var cx = 0f; var cy = 0f; var angle = 0f; var w = 0f; var h = 0f; var alpha = 0f
        var fl = 1f; var fw = 1f; var uz = 0f; var face = 1f
        var tcx = 0f; var tcy = 0f; var tAngle = 0f; var tw = 0f; var th = 0f; var tAlpha = 0f
        var tfl = 1f; var tfw = 1f; var tuz = 0f; var tface = 1f
        var vx = 0f; var vy = 0f            // target velocity, px per ms
        var lastTargetMs = 0L

        fun setTarget(cx: Float, cy: Float, angle: Float, w: Float, h: Float, tilt: Tilt) {
            val now = SystemClock.uptimeMillis()
            if (alpha < 0.02f) {                       // (re)appearing: jump straight to the finger
                this.cx = cx; this.cy = cy; this.angle = angle; this.w = w; this.h = h
                fl = tilt.fl; fw = tilt.fw; uz = tilt.uz; face = tilt.facing
                vx = 0f; vy = 0f
            } else if (lastTargetMs > 0L && tAlpha > 0f) {
                val dt = (now - lastTargetMs).toFloat()
                if (dt in 15f..400f) {
                    vx = vx * 0.5f + 0.5f * (cx - tcx) / dt
                    vy = vy * 0.5f + 0.5f * (cy - tcy) / dt
                }
            }
            lastTargetMs = now
            tcx = cx; tcy = cy; tw = w; th = h; tAlpha = 1f
            tfl = tilt.fl; tfw = tilt.fw; tuz = tilt.uz; tface = tilt.facing
            // turn the short way round (e.g. 350° -> 10° is +20°, not -340°)
            var d = (angle - this.angle) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            tAngle = this.angle + d
        }
    }

    private val states = Array(10) { State() }

    /** Tracking delay in ms (camera frame -> result), set by the overlay each frame. */
    @Volatile var latencyMs = 0

    /** Call whenever a new tracking result arrives. */
    fun setTargets(frame: HandFrame, viewW: Float, viewH: Float, nails: List<Bitmap?>, showAlways: Boolean) {
        states.forEach { it.tAlpha = 0f }
        if (frame.width <= 0 || viewW <= 0f) return
        val mapper = FrameMapper(frame.width, frame.height, viewW, viewH)

        val left = frame.hands.filter { it.isLeft }.maxByOrNull { it.score }
        val right = frame.hands.filter { !it.isLeft }.maxByOrNull { it.score }

        for (hand in listOfNotNull(left, right)) {
            if (!hand.isBackOfHand && !showAlways) continue      // nails aren't visible from the palm side
            val pts = hand.points.map(mapper::map)
            val knuckle = (dist(pts[5], pts[9]) + dist(pts[9], pts[13]) + dist(pts[13], pts[17])) / 3f
            val world = hand.world.takeIf { NailTuning.USE_TILT && it.size >= 21 }?.map { it.v3() }

            for (finger in Finger.entries) {
                val index = if (hand.isLeft) finger.ordinal else 9 - finger.ordinal
                val bmp = nails.getOrNull(index) ?: continue

                val dip = pts[finger.dip]
                val tip = pts[finger.tip]
                val dx = tip.x - dip.x
                val dy = tip.y - dip.y
                val len = hypot(dx, dy)
                if (len < 6f) continue
                val ux = dx / len
                val uy = dy / len

                val width = knuckle * finger.widthFactor * NailTuning.sizeScale
                val height = width * bmp.height / bmp.width
                val base = NailTuning.baseFrac + finger.baseBias
                val baseX = tip.x - ux * base * len
                val baseY = tip.y - uy * base * len
                val cx = baseX + ux * height / 2f
                val cy = baseY + uy * height / 2f
                // The nail artwork has its cuticle at the TOP of the image and its free edge at the BOTTOM,
                // so it is turned 180° from the finger direction (clockwise from "up", +180).
                val angle = atan2(ux, -uy) * 180f / PI.toFloat() + NailTuning.ART_ROTATION

                val tilt = if (world != null) tiltFor(world, finger, hand.isLeft) else Tilt.FLAT
                states[index].setTarget(cx, cy, angle, width, height, tilt)
            }
        }
    }

    /**
     * 3D tilt of the nail on [finger]. The nail lies on the back of the last finger segment:
     *  - u = direction of that segment, l = across the knuckles, nail normal = u x l (same handedness as the hand's own normal).
     *  - fl: how much of the nail's length is still visible (finger pointing at the camera -> short).
     *  - fw: how much of its width is visible (finger rolled sideways -> narrow).
     *  - facing: cos of the angle between the nail normal and the camera ray (curled away -> 0 or negative).
     */
    private fun tiltFor(w: List<V3>, finger: Finger, isLeft: Boolean): Tilt {
        // World z < 0 = towards the camera. (wrist->index) x (wrist->pinky) points out of the BACK of a left
        // hand and out of the PALM of a right hand (checked on real frames), hence the sign per hand.
        val sgn = if (isLeft) 1f else -1f
        val handNormal = (w[5] - w[0]).cross(w[17] - w[0])

        val u = (w[finger.tip] - w[finger.dip]).unit()
        if (finger == Finger.Thumb) {
            // The thumb is rolled about 55° from the rest of the hand's back, towards its outer side.
            val back = handNormal.unit() * sgn                       // out of the back of the hand
            var side = u.cross(back).unit()
            if (side.dot(w[5] - w[2]) > 0f) side = side * -1f        // pointing away from the index finger
            val phi = 55f * (PI.toFloat() / 180f)
            val nt = (back * cos(phi) + side * sin(phi)).unit()
            val across = nt.cross(u).unit()
            return Tilt(
                fl = hypot(u.x, u.y).coerceIn(0.4f, 1f),
                fw = hypot(across.x, across.y).coerceIn(0.65f, 1f),
                uz = u.z.coerceIn(-1f, 1f),
                facing = -nt.z
            )
        }
        val l = (w[17] - w[5]).unit()
        val lp = (l - u * l.dot(u)).unit()
        val nf = u.cross(l).unit()
        val facing = -sgn * nf.z

        return Tilt(
            fl = hypot(u.x, u.y).coerceIn(0.35f, 1f),
            fw = hypot(lp.x, lp.y).coerceIn(0.5f, 1f),
            uz = u.z.coerceIn(-1f, 1f),   // + = fingertip further from the camera than the knuckle
            facing = facing
        )
    }

    /** Call once per display frame. [dtMs] = milliseconds since the previous call. */
    fun step(dtMs: Float) {
        val now = SystemClock.uptimeMillis()
        for (s in states) {
            if (s.alpha < 0.01f && s.tAlpha == 0f) continue
            // Where the finger probably is NOW: the last result is `latencyMs` + `age` old, so carry it forward
            // along the measured motion (ignored for tiny, jittery speeds).
            var gx = s.tcx
            var gy = s.tcy
            val v = hypot(s.vx, s.vy)
            if (NailTuning.predict && v > 0.03f && s.tAlpha > 0f) {
                // The preview itself is ~40 ms behind real time, so only the rest of the delay is made up; 0.7 = damped
                // so a jittery speed estimate cannot throw the nail far ahead of the finger.
                val delayMs = max(0, latencyMs - 40).toFloat() + (now - s.lastTargetMs).toFloat()
                val lead = 0.7f * delayMs.coerceIn(0f, 280f)
                gx += s.vx * lead
                gy += s.vy * lead
            }
            // The faster a nail has to travel, the less it is smoothed (keeps up with quick hands, calm when still).
            val gap = hypot(gx - s.cx, gy - s.cy)
            val speed = gap / max(s.h, 1f)
            // Nearly still (gap under 4 px) -> heavy smoothing so tracking noise doesn't make the nail shimmer.
            val tau = if (gap < 4f) 160f else (110f - 500f * speed).coerceIn(25f, 110f)
            val k = 1f - exp(-dtMs / tau)
            s.cx += (gx - s.cx) * k
            s.cy += (gy - s.cy) * k
            val tauAngle = (200f - 900f * speed).coerceIn(45f, 200f)
            s.angle += (s.tAngle - s.angle) * (1f - exp(-dtMs / tauAngle))
            val kSize = 1f - exp(-dtMs / 120f)
            s.w += (s.tw - s.w) * kSize
            s.h += (s.th - s.h) * kSize
            // 3D values are noisier than the 2D ones, so they get a calmer filter.
            val kTilt = 1f - exp(-dtMs / 110f)
            s.fl += (s.tfl - s.fl) * kTilt
            s.fw += (s.tfw - s.fw) * kTilt
            s.uz += (s.tuz - s.uz) * kTilt
            s.face += (s.tface - s.face) * kTilt
            // A nail that is missing from one or two results (tracking dropout) is held for 150 ms instead of blinking out.
            val wanted = if (s.tAlpha > 0f || now - s.lastTargetMs < 150L) 1f else 0f
            val kAlpha = 1f - exp(-dtMs / 90f)
            s.alpha += (wanted - s.alpha) * kAlpha
        }
    }

    /**
     * Draws are described by the nail's centre, rotation, nominal size and tilt.
     * [alpha] already includes the fade for nails that are curling away from the camera.
     */
    fun forEachVisible(
        block: (index: Int, cx: Float, cy: Float, angleDeg: Float, w: Float, h: Float, alpha: Float, fl: Float, fw: Float, uz: Float) -> Unit
    ) {
        states.forEachIndexed { i, s ->
            val fade = ((s.face - NailTuning.FADE_LOW) / (NailTuning.FADE_HIGH - NailTuning.FADE_LOW)).coerceIn(0f, 1f)
            val a = s.alpha * fade * fade * (3f - 2f * fade)          // smoothstep
            if (a > 0.02f && s.w > 1f && s.h > 1f) block(i, s.cx, s.cy, s.angle, s.w, s.h, a, s.fl, s.fw, s.uz)
        }
    }

    /** How steady the nails are right now (used by auto-capture). Sizes are relative to the nail's own height. */
    class Steadiness(
        /** Nails that are fully shown and facing the camera. */
        val visible: Int,
        /** Largest distance between where a nail is drawn and where tracking says it belongs (fraction of nail height). */
        val maxGap: Float,
        /** Fastest nail movement, in nail-heights per second. */
        val maxSpeed: Float
    )

    fun steadiness(): Steadiness {
        var n = 0
        var gap = 0f
        var speed = 0f
        for (s in states) {
            if (s.alpha > 0.9f && s.tAlpha > 0f && s.face > NailTuning.FADE_HIGH && s.h > 1f) {
                n++
                gap = max(gap, hypot(s.tcx - s.cx, s.tcy - s.cy) / s.h)
                speed = max(speed, hypot(s.vx, s.vy) * 1000f / s.h)
            }
        }
        return Steadiness(n, gap, speed)
    }

    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
}
