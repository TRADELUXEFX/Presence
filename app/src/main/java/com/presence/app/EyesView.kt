package com.presence.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.PathParser
import java.util.Random
import kotlin.math.exp
import kotlin.math.sin

/**
 * The website's "animated eyes" (dark mirror mode): a dark screen with a face that blinks,
 * drifts its gaze smoothly, and locks eye contact while a question is read. Drawn in the website's
 * 340 x 280 coordinate space so it matches the SVG.
 */
class EyesView(ctx: Context) : View(ctx) {
    private val rnd = Random()

    private var active = false
    private var phase = "idle"
    private var lx = 91f
    private var rx = 249f
    private var py = 86f
    private var locking = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun path(d: String): Path = PathParser.createPathFromPathData(d)
    private fun argb(a: Float, r: Int, g: Int, b: Int): Int = Color.argb((a * 255f + 0.5f).toInt(), r, g, b)

    private val lOutline = path("M30,82 C48,60 108,56 138,68 C148,73 152,80 152,88 C148,100 108,112 60,108 C38,104 26,96 30,82 Z")
    private val rOutline = path("M310,82 C292,60 232,56 202,68 C192,73 188,80 188,88 C192,100 232,112 280,108 C302,104 314,96 310,82 Z")
    private val lLiner = path("M30,82 C48,60 108,56 138,68 C148,73 152,80 152,88")
    private val rLiner = path("M310,82 C292,60 232,56 202,68 C192,73 188,80 188,88")
    private val lWing = path("M152,88 C158,82 162,76 160,70")
    private val rWing = path("M188,88 C182,82 178,76 180,70")
    private val lLower = path("M30,82 C38,104 60,108 108,112 C130,113 148,106 152,88")
    private val rLower = path("M310,82 C302,104 280,108 232,112 C210,113 192,106 188,88")
    private val lCrease = path("M36,76 C65,58 118,57 150,74")
    private val rCrease = path("M304,76 C275,58 222,57 190,74")
    private val lBrow = path("M42,56 C62,40 94,34 138,44")
    private val rBrow = path("M298,56 C278,40 246,34 202,44")
    private val noseL = path("M155,104 C153,118 151,134 150,148 C148,154 144,158 140,162")
    private val noseR = path("M185,104 C187,118 189,134 190,148 C192,154 196,158 200,162")
    private val nostrilL = path("M140,162 C132,160 126,165 128,170 C131,175 142,175 150,169")
    private val nostrilR = path("M200,162 C208,160 214,165 212,170 C209,175 198,175 190,169")
    private val lowerLip = path("M134,210 C140,228 156,236 170,236 C184,236 200,228 206,210 C194,202 146,202 134,210 Z")
    private val upperLip = path("M134,210 C137,203 148,195 158,197 C163,198 167,203 170,206 C173,203 177,198 182,197 C192,195 203,203 206,210 C196,205 184,202 178,204 C175,205 172,208 170,210 C168,208 165,205 162,204 C156,202 144,205 134,210 Z")
    private val shimmer = path("M157,198 C163,194 170,199 170,199 C170,199 177,194 183,198")
    private val seam = path("M134,210 C150,206 164,208 170,208 C176,208 190,206 206,210")

    private val lashesL = floatArrayOf(50f, 67f, 44f, 55f, 67f, 60f, 64f, 47f, 84f, 57f, 83f, 43f, 101f, 57f, 102f, 43f, 117f, 60f, 120f, 47f, 132f, 67f, 137f, 55f, 144f, 76f, 151f, 66f)
    private val lashesR = floatArrayOf(290f, 67f, 296f, 55f, 273f, 60f, 276f, 47f, 256f, 57f, 257f, 43f, 239f, 57f, 238f, 43f, 223f, 60f, 220f, 47f, 208f, 67f, 203f, 55f, 196f, 76f, 189f, 66f)

    private val scratch = Path()
    private val matrix = Matrix()
    private val rect = RectF()

    private fun radial(cx: Float, cy: Float, w: Float, hgt: Float, px: Float, pyy: Float, pr: Float, colors: IntArray, stops: FloatArray): Shader {
        val ccx = cx + (px - 0.5f) * w
        val ccy = cy + (pyy - 0.5f) * hgt
        val s = RadialGradient(ccx, ccy, pr * w, colors, stops, Shader.TileMode.CLAMP)
        if (hgt != w) {
            val m = Matrix()
            m.setScale(1f, hgt / w, ccx, ccy)
            s.setLocalMatrix(m)
        }
        return s
    }

    private val scleraCols = intArrayOf(Color.parseColor("#FEFAF8"), Color.parseColor("#EDE5DC"), Color.parseColor("#C8B8AE"))
    private val scleraStops = floatArrayOf(0f, 0.65f, 1f)
    private val irisCols = intArrayOf(Color.parseColor("#7ECBA0"), Color.parseColor("#3A8C5C"), Color.parseColor("#1E5C38"), Color.parseColor("#0A2018"))
    private val irisStops = floatArrayOf(0f, 0.28f, 0.65f, 1f)
    private val pupilCols = intArrayOf(Color.parseColor("#1A1A1A"), Color.BLACK)
    private val pupilStops = floatArrayOf(0f, 1f)

    private val scleraL = radial(91f, 86f, 128f, 68f, 0.48f, 0.38f, 0.58f, scleraCols, scleraStops)
    private val scleraR = radial(249f, 86f, 128f, 68f, 0.48f, 0.38f, 0.58f, scleraCols, scleraStops)
    private val irisL = radial(91f, 86f, 48f, 48f, 0.44f, 0.32f, 0.56f, irisCols, irisStops)
    private val irisR = radial(249f, 86f, 48f, 48f, 0.44f, 0.32f, 0.56f, irisCols, irisStops)
    private val pupilL = radial(91f, 86f, 22f, 22f, 0.36f, 0.32f, 0.56f, pupilCols, pupilStops)
    private val pupilR = radial(249f, 86f, 22f, 22f, 0.36f, 0.32f, 0.56f, pupilCols, pupilStops)
    private val lidShadow: Shader = LinearGradient(
        0f, 52f, 0f, 86f,
        intArrayOf(argb(0.82f, 0x18, 0x0a, 0x12), argb(0.35f, 0x2a, 0x10, 0x20), argb(0f, 0, 0, 0)),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val lowerLipShader = radial(170f, 219f, 72f, 34f, 0.5f, 0.35f, 0.62f,
        intArrayOf(Color.parseColor("#E8938A"), Color.parseColor("#C46060"), Color.parseColor("#882828")), floatArrayOf(0f, 0.5f, 1f))
    private val upperLipShader = radial(170f, 203f, 72f, 15f, 0.5f, 0.28f, 0.65f,
        intArrayOf(Color.parseColor("#D07570"), Color.parseColor("#A84545"), Color.parseColor("#762020")), floatArrayOf(0f, 0.55f, 1f))

    private fun stroke(c: Canvas, p: Path, color: Int, w: Float) {
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        line.color = color
        line.strokeWidth = w
        c.drawPath(p, line)
    }

    private fun drawEye(
        c: Canvas, cx: Float, px: Float, outline: Path, sclera: Shader, iris: Shader, pupil: Shader,
        corner: Float, catchX: Float, shadowX: Float, lidAmt: Float
    ) {
        val scaleY = 1f - lidAmt
        if (scaleY > 0.02f) {
            matrix.setScale(1f, scaleY, cx, 86f)
            outline.transform(matrix, scratch)
            c.save()
            c.clipPath(scratch)
            fill.style = Paint.Style.FILL
            fill.color = Color.BLACK   // a shader is dimmed by the paint's alpha: start every eye from a fully opaque paint
            fill.shader = sclera
            rect.set(cx - 64f, 52f, cx + 64f, 120f)
            c.drawOval(rect, fill)
            fill.shader = null
            fill.color = argb(0.5f, 0xdf, 0xa0, 0xa0)
            rect.set(corner - 8f, 87f - 5.5f, corner + 8f, 87f + 5.5f)
            c.drawOval(rect, fill)
            // iris + pupil follow the gaze
            c.save()
            c.translate(px - cx, py - 86f)
            fill.color = Color.BLACK
            fill.shader = iris
            c.drawCircle(cx, 86f, 24f, fill)
            fill.shader = null
            line.shader = null
            line.style = Paint.Style.STROKE
            line.color = argb(0.4f, 0x16, 0x40, 0x28)
            line.strokeWidth = 1.8f
            c.drawCircle(cx, 86f, 24f, line)
            line.color = argb(0.3f, 0x0a, 0x28, 0x18)
            line.strokeWidth = 1f
            c.drawCircle(cx, 86f, 18f, line)
            fill.color = Color.BLACK
            fill.shader = pupil
            c.drawCircle(cx, 86f, pupilRad, fill)
            fill.shader = null
            c.restore()
            // catchlights stay put
            fill.color = argb(0.92f, 255, 255, 255)
            c.drawCircle(catchX + cgx, 79f + cgy, 4.5f, fill)
            fill.color = argb(0.38f, 255, 255, 255)
            c.drawCircle(catchX + 13f + cgx, 93f + cgy, 2.2f, fill)
            // lid shadow
            fill.color = Color.BLACK
            fill.shader = lidShadow
            c.drawRect(shadowX, 52f, shadowX + 130f, 86f, fill)
            fill.shader = null
            c.restore()
            stroke(c, scratch, Color.parseColor("#0C0408"), 1.6f)
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.parseColor("#0A0A0A"))
        val s = Math.min(width / 340f, height / 280f) * 0.92f
        c.save()
        c.translate((width - 340f * s) / 2f, (height - 280f * s) / 2f)
        c.scale(s, s)
        c.translate(swayX, swayY)

        // eyebrows (they lift when listening and follow the gaze a little)
        c.save()
        c.translate(0f, browY)
        stroke(c, lBrow, Color.parseColor("#1A0E08"), 6f)
        stroke(c, lBrow, argb(0.5f, 0x2e, 0x1a, 0x0c), 3.5f)
        stroke(c, rBrow, Color.parseColor("#1A0E08"), 6f)
        stroke(c, rBrow, argb(0.5f, 0x2e, 0x1a, 0x0c), 3.5f)
        c.restore()

        // eyes
        drawEye(c, 91f, lx, lOutline, scleraL, irisL, pupilL, 32f, 84f, 26f, lidL)
        drawEye(c, 249f, rx, rOutline, scleraR, irisR, pupilR, 308f, 242f, 184f, lidR)
        val ink = Color.parseColor("#060204")
        stroke(c, lLiner, ink, 4.5f)
        stroke(c, lWing, ink, 3f)
        stroke(c, lLower, Color.parseColor("#180810"), 1.3f)
        stroke(c, lCrease, argb(0.22f, 90, 35, 55), 1.8f)
        stroke(c, rLiner, ink, 4.5f)
        stroke(c, rWing, ink, 3f)
        stroke(c, rLower, Color.parseColor("#180810"), 1.3f)
        stroke(c, rCrease, argb(0.22f, 90, 35, 55), 1.8f)
        lashes(c, lashesL)
        lashes(c, lashesR)

        // nose
        stroke(c, noseL, argb(0.2f, 160, 90, 70), 1.8f)
        stroke(c, noseR, argb(0.2f, 160, 90, 70), 1.8f)
        stroke(c, nostrilL, argb(0.55f, 100, 50, 40), 2f)
        stroke(c, nostrilR, argb(0.55f, 100, 50, 40), 2f)
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = argb(0.1f, 255, 220, 200)
        rect.set(162f, 157f, 178f, 167f)
        c.drawOval(rect, fill)

        // lips
        fill.shader = lowerLipShader
        c.drawPath(lowerLip, fill)
        fill.shader = null
        fill.color = argb(0.3f, 255, 225, 215)
        rect.set(148f, 215f, 192f, 229f)
        c.drawOval(rect, fill)
        fill.color = argb(0.18f, 255, 240, 235)
        c.save()
        c.rotate(-8f, 162f, 219f)
        rect.set(153f, 215f, 171f, 223f)
        c.drawOval(rect, fill)
        c.restore()
        fill.shader = upperLipShader
        c.drawPath(upperLip, fill)
        fill.shader = null
        stroke(c, shimmer, argb(0.45f, 255, 200, 185), 1f)
        stroke(c, seam, Color.parseColor("#5E1E1E"), 1.5f)
        fill.style = Paint.Style.FILL
        fill.color = argb(0.65f, 0x48, 0x14, 0x14)
        c.drawCircle(134f, 210f, 3f, fill)
        c.drawCircle(206f, 210f, 3f, fill)

        c.restore()
    }

    private fun lashes(c: Canvas, pts: FloatArray) {
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.color = Color.parseColor("#08040A")
        line.strokeWidth = 1.6f
        var i = 0
        while (i + 3 < pts.size) {
            c.drawLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], line)
            i += 4
        }
    }

    // ---------- animation (one frame loop: smooth gaze, micro-movements, living pupils) ----------

    // gaze: current + target offsets from the centre, plus micro jitter on top
    private var gx = 0f
    private var gy = 0f
    private var tgx = 0f
    private var tgy = 0f
    private var gazeRate = 6f
    private var jx = 0f
    private var jy = 0f
    private var tjx = 0f
    private var tjy = 0f
    private var returnToCenter = false
    private var nextDriftAt = 0L
    private var nextJitterAt = 0L

    // blinking (each eye has its own lid, the right one lags a few ms)
    private var lidL = 0f
    private var lidR = 0f
    private var blinkStart = -1_000_000L
    private var blinkDur = 256L
    private var blinkDelayR = 0L
    private var nextBlinkAt = 0L
    private var nextIsSecond = false

    // face details
    private var pupilRad = 11f
    private var dilate = 0f
    private var browLift = 0f
    private var browY = 0f
    private var cgx = 0f
    private var cgy = 0f
    private var swayX = 0f
    private var swayY = 0f
    private var lastT = 0L

    private fun blinkMin(): Int = when (phase) { "listen" -> 1800; "pause" -> 4000; "answer" -> 900; else -> 2200 }
    private fun blinkMax(): Int = when (phase) { "listen" -> 3800; "pause" -> 7500; "answer" -> 2200; else -> 5500 }

    private val frameRun = Runnable { frame() }

    private fun scheduleBlinkSoon(now: Long) {
        val m = blinkMin()
        nextIsSecond = false
        nextBlinkAt = now + (m * 0.3 + rnd.nextDouble() * m * 0.4).toLong()
    }

    private fun startBlink(now: Long, second: Boolean) {
        val slow = phase == "pause"
        val k = 0.85f + rnd.nextFloat() * 0.4f
        blinkDur = ((if (slow) 400f else 256f) * k).toLong()
        blinkStart = now
        blinkDelayR = (rnd.nextFloat() * 28f).toLong()
        val double = !second && !slow && rnd.nextFloat() < 0.2f
        if (double) {
            nextIsSecond = true
            nextBlinkAt = now + blinkDur + 120L
        } else {
            nextIsSecond = false
            val lo = blinkMin()
            val hi = blinkMax()
            nextBlinkAt = now + (lo + rnd.nextDouble() * (hi - lo)).toLong()
        }
    }

    private fun lidAt(t: Long): Float {
        if (t <= 0L || t >= blinkDur) return 0f
        val f = t.toFloat() / blinkDur
        val v = if (f < 0.44f) f / 0.44f else 1f - (f - 0.44f) / 0.56f
        return v * v * (3f - 2f * v)
    }

    private fun pickGaze(now: Long) {
        if (returnToCenter) {
            returnToCenter = false
            tgx = (rnd.nextFloat() - 0.5f) * 4f
            tgy = (rnd.nextFloat() - 0.5f) * 2f
            gazeRate = 7f
            nextDriftAt = now + 700L + rnd.nextInt(1800)
        } else if (rnd.nextFloat() < 0.3f) {
            // a bigger look to the side / up / down, held for a moment, then back to centre
            tgx = (rnd.nextFloat() - 0.5f) * 26f
            tgy = (rnd.nextFloat() - 0.5f) * 13f
            gazeRate = 13f
            returnToCenter = true
            nextDriftAt = now + 1200L + rnd.nextInt(2300)
        } else {
            tgx = (rnd.nextFloat() - 0.5f) * 16f
            tgy = (rnd.nextFloat() - 0.5f) * 8f
            gazeRate = 5f + rnd.nextFloat() * 4f
            nextDriftAt = now + 800L + rnd.nextInt(2200)
        }
    }

    private fun frame() {
        if (!active) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastT).coerceIn(1L, 64L) / 1000f
        lastT = now

        // gaze: ease toward the target (locks to the centre while a question is read)
        if (locking) {
            tgx = 0f; tgy = 0f; gazeRate = 5f
        } else if (now >= nextDriftAt) {
            pickGaze(now)
        }
        val a = 1f - exp(-gazeRate * dt)
        gx += (tgx - gx) * a
        gy += (tgy - gy) * a

        // micro movements
        if (now >= nextJitterAt) {
            val amp = if (locking) 0.25f else 1f
            tjx = (rnd.nextFloat() - 0.5f) * 1.0f * amp
            tjy = (rnd.nextFloat() - 0.5f) * 0.7f * amp
            nextJitterAt = now + 120L + rnd.nextInt(140)
        }
        val aj = 1f - exp(-30f * dt)
        jx += (tjx - jx) * aj
        jy += (tjy - jy) * aj

        lx = 91f + gx + jx
        rx = 249f + gx + jx
        py = 86f + gy + jy

        // blinks (per-eye lids, occasional double blink) + upper lids lower a touch when looking down
        if (now >= nextBlinkAt) startBlink(now, nextIsSecond)
        val bias = Math.max(0f, gy + jy) * 0.01f
        lidL = Math.min(1f, lidAt(now - blinkStart) + bias)
        lidR = Math.min(1f, lidAt(now - blinkStart - blinkDelayR) + bias)

        // pupils breathe and widen slightly while listening / answering
        val dilTarget = when (phase) { "listen" -> 1.3f; "answer" -> 0.7f; else -> 0f }
        dilate += (dilTarget - dilate) * (1f - exp(-2f * dt))
        pupilRad = 11f + dilate + 0.6f * sin(now / 1300f)

        // brows lift when listening, settle when answering, and follow the gaze a little
        val browTarget = when (phase) { "listen" -> -3f; "answer" -> 1.5f; else -> 0f }
        browLift += (browTarget - browLift) * (1f - exp(-4f * dt))
        browY = browLift + gy * 0.25f

        // catchlights drift with the gaze, the whole face sways like breathing
        cgx = (gx + jx) * 0.25f
        cgy = (gy + jy) * 0.25f
        swayX = 1.2f * sin(now / 2100f)
        swayY = 1.0f * sin(now / 2900f + 1f)

        invalidate()
        postOnAnimation(frameRun)
    }

    /** phase: "listen", "pause", "answer" or "idle". Safe to call every second. */
    fun setPhase(p: String) {
        if (p == phase) return
        phase = p
        if (!active) return
        val now = SystemClock.uptimeMillis()
        scheduleBlinkSoon(now)
        if (phase == "listen") {
            locking = true
        } else if (locking) {
            locking = false
            returnToCenter = false
            nextDriftAt = now + 400L + rnd.nextInt(800)
        }
    }

    private fun begin() {
        if (active) return
        active = true
        val now = SystemClock.uptimeMillis()
        locking = phase == "listen"
        gx = 0f; gy = 0f; tgx = 0f; tgy = 0f
        jx = 0f; jy = 0f; tjx = 0f; tjy = 0f
        returnToCenter = false
        blinkStart = -1_000_000L
        lidL = 0f; lidR = 0f
        pupilRad = 11f; dilate = 0f
        browLift = 0f; browY = 0f
        lastT = now
        nextDriftAt = now + 600L
        nextJitterAt = now
        scheduleBlinkSoon(now)
        removeCallbacks(frameRun)
        postOnAnimation(frameRun)
        invalidate()
    }

    private fun end() {
        active = false
        locking = false
        removeCallbacks(frameRun)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) begin() else if (visibility != VISIBLE) end()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && this.visibility == VISIBLE) begin() else if (visibility != VISIBLE) end()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) begin()
    }

    override fun onDetachedFromWindow() {
        end()
        super.onDetachedFromWindow()
    }
}
