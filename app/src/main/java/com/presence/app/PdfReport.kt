package com.presence.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * A designed summary of the trainings in the chosen range, laid out like PauzFirst's PDF:
 * branded header with the logo, total tiles, a breakdown by training, then the full list.
 * Built with Android's own PdfDocument and the app's Poppins font, so it works offline.
 */
object PdfReport {

    class Row(val date: String, val name: String, val details: String, val duration: String)

    private const val W = 595          // A4 in points
    private const val H = 842
    private const val M = 36f          // page margin
    private const val BOTTOM = 58f     // space kept free for the footer

    private val BLUE = Color.parseColor("#1E90FF")
    private val BLUE_DARK = Color.parseColor("#0B5FD0")
    private val DARK = Color.parseColor("#0F172A")
    private val MUTED = Color.parseColor("#64748B")
    private val LINE = Color.parseColor("#E2E8F0")
    private val ZEBRA = Color.parseColor("#F6F9FD")
    private val TEAL = Color.parseColor("#14B8A6")
    private val VIOLET = Color.parseColor("#8B5CF6")
    private val AMBER = Color.parseColor("#F59E0B")
    private val ACCENTS = intArrayOf(BLUE, TEAL, VIOLET, AMBER)

    private class Face(val reg: Typeface, val med: Typeface, val bold: Typeface)

    private fun paint(tf: Typeface, size: Float, color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.typeface = tf
        return p
    }

    private fun fill(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.FILL
        return p
    }

    private fun stroke(color: Int, width: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        return p
    }

    private class Ctx(val doc: PdfDocument, val footer: String, val face: Face) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas = Canvas()
        var y = 0f
        var n = 0

        fun next() {
            finish()
            n++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            page = p
            canvas = p.canvas
            y = M
        }

        fun finish() {
            val p = page ?: return
            val f = paint(face.reg, 8.5f, MUTED)
            canvas.drawLine(M, H - 40f, W - M, H - 40f, stroke(LINE, 0.8f))
            canvas.drawText(footer, M, H - 21f, f)
            val num = "Page $n"
            canvas.drawText(num, W - M - f.measureText(num), H - 21f, f)
            doc.finishPage(p)
            page = null
        }

        /** Starts a new page if [h] more points would run into the footer. */
        fun ensure(h: Float) {
            if (y + h > H - BOTTOM) next()
        }
    }

    private fun drawLogo(c: Canvas, bmp: Bitmap, box: RectF) {
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val l = box.left + (box.width() - w) / 2f
        val t = box.top + (box.height() - h) / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(bmp, null, RectF(l, t, l + w, t + h), p)
    }

    private fun centered(c: Canvas, s: String, cx: Float, y: Float, p: Paint) {
        c.drawText(s, cx - p.measureText(s) / 2f, y, p)
    }

    /** Shortens [s] with an ellipsis so it fits in [maxW] points. */
    private fun fit(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText(t + "\u2026") > maxW) t = t.dropLast(1)
        return t + "\u2026"
    }

    /** Section title with a small blue accent bar in front of it. */
    private fun heading(ctx: Ctx, s: String) {
        ctx.ensure(30f)
        val c = ctx.canvas
        c.drawRoundRect(RectF(M, ctx.y + 1f, M + 4f, ctx.y + 17f), 2f, 2f, fill(BLUE))
        c.drawText(s, M + 12f, ctx.y + 13.5f, paint(ctx.face.bold, 13f, DARK))
        ctx.y += 28f
    }

    private fun tableHeader(ctx: Ctx) {
        val c = ctx.canvas
        val top = ctx.y
        c.drawRoundRect(RectF(M, top, W - M, top + 24f), 8f, 8f, fill(BLUE))
        val p = paint(ctx.face.bold, 9f, Color.WHITE)
        val base = top + 16f
        c.drawText("Date", M + 12f, base, p)
        c.drawText("Training", M + 150f, base, p)
        c.drawText("Details", M + 262f, base, p)
        c.drawText("Duration", M + 462f, base, p)
        ctx.y += 30f
    }

    /**
     * @param totals      (label, value) pairs shown as tiles at the top
     * @param byTraining  (training name, count) pairs, only trainings that appear
     * @param rows        one line per training, in the order shown on the Progress screen
     */
    fun write(
        context: Context,
        file: File,
        rangeLabel: String,
        period: String,
        generated: String,
        totals: List<Pair<String, String>>,
        byTraining: List<Pair<String, Int>>,
        rows: List<Row>
    ) {
        val face = Face(Fonts.regular(context), Fonts.medium(context), Fonts.bold(context))
        val logo: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }
        val doc = PdfDocument()
        try {
            val ctx = Ctx(doc, "Presence  \u2022  " + rangeLabel, face)
            ctx.next()
            val c = ctx.canvas

            // ---------- header band with gradient, soft circles and the logo ----------
            val band = Paint(Paint.ANTI_ALIAS_FLAG)
            band.shader = LinearGradient(0f, 0f, W.toFloat(), 130f, BLUE, BLUE_DARK, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), 130f, band)
            c.drawCircle(W - 40f, 10f, 90f, fill(Color.argb(26, 255, 255, 255)))
            c.drawCircle(W - 150f, 140f, 70f, fill(Color.argb(18, 255, 255, 255)))

            // white rounded chip so the logo is always clearly visible
            val chip = RectF(M, 30f, M + 66f, 96f)
            c.drawRoundRect(chip, 18f, 18f, fill(Color.WHITE))
            if (logo != null) {
                drawLogo(c, logo, RectF(chip.left + 10f, chip.top + 10f, chip.right - 10f, chip.bottom - 10f))
            }
            c.drawText("Presence", M + 82f, 62f, paint(face.bold, 26f, Color.WHITE))
            c.drawText("Training summary", M + 82f, 82f, paint(face.med, 12f, Color.argb(225, 255, 255, 255)))

            // range as a pill on the right, period under it
            val rl = paint(face.bold, 12.5f, BLUE_DARK)
            val pillW = rl.measureText(rangeLabel) + 28f
            val pill = RectF(W - M - pillW, 36f, W - M, 60f)
            c.drawRoundRect(pill, 12f, 12f, fill(Color.WHITE))
            centered(c, rangeLabel, pill.centerX(), 52.5f, rl)
            val pp = paint(face.med, 10.5f, Color.WHITE)
            c.drawText(period, W - M - pp.measureText(period), 80f, pp)

            c.drawText(generated, M, 150f, paint(face.reg, 8.5f, MUTED))
            ctx.y = 164f

            // ---------- total tiles ----------
            if (totals.isNotEmpty()) {
                val gap = 10f
                val tw = (W - 2 * M - gap * (totals.size - 1)) / totals.size
                for ((i, t) in totals.withIndex()) {
                    val x = M + i * (tw + gap)
                    val r = RectF(x, ctx.y, x + tw, ctx.y + 66f)
                    val cv = ctx.canvas
                    cv.drawRoundRect(r, 14f, 14f, fill(Color.WHITE))
                    cv.drawRoundRect(r, 14f, 14f, stroke(LINE, 1f))
                    cv.drawRoundRect(RectF(x + tw / 2f - 14f, ctx.y + 9f, x + tw / 2f + 14f, ctx.y + 12.5f), 2f, 2f, fill(ACCENTS[i % ACCENTS.size]))
                    centered(cv, t.second, x + tw / 2f, ctx.y + 37f, paint(face.bold, 19f, DARK))
                    centered(cv, t.first, x + tw / 2f, ctx.y + 55f, paint(face.reg, 9f, MUTED))
                }
                ctx.y += 66f + 26f
            }

            // ---------- breakdown by training ----------
            if (byTraining.isNotEmpty()) {
                heading(ctx, "By training")
                val total = byTraining.sumOf { it.second }.coerceAtLeast(1)
                val barW = W - 2 * M - 150f
                for ((i, b) in byTraining.withIndex()) {
                    ctx.ensure(22f)
                    val cv = ctx.canvas
                    cv.drawText(b.first, M, ctx.y + 11f, paint(face.med, 10f, DARK))
                    val track = RectF(M + 120f, ctx.y + 3f, M + 120f + barW, ctx.y + 11f)
                    cv.drawRoundRect(track, 4f, 4f, fill(LINE))
                    val frac = b.second.toFloat() / total
                    if (frac > 0f) {
                        cv.drawRoundRect(RectF(track.left, track.top, track.left + barW * frac, track.bottom), 4f, 4f, fill(ACCENTS[i % ACCENTS.size]))
                    }
                    cv.drawText(b.second.toString(), track.right + 8f, ctx.y + 11f, paint(face.bold, 10f, DARK))
                    ctx.y += 20f
                }
                ctx.y += 14f
            }

            // ---------- the full list ----------
            heading(ctx, "Trainings")
            ctx.ensure(30f)
            tableHeader(ctx)
            for ((i, r) in rows.withIndex()) {
                val before = ctx.n
                ctx.ensure(22f)
                if (ctx.n != before) {
                    // a new page starts, so the column titles start again
                    tableHeader(ctx)
                }
                val cv = ctx.canvas
                if (i % 2 == 1) cv.drawRect(M, ctx.y, W - M, ctx.y + 22f, fill(ZEBRA))
                val base = ctx.y + 15f
                val body = paint(face.reg, 9f, DARK)
                cv.drawText(fit(r.date, body, 136f), M + 12f, base, body)
                cv.drawText(fit(r.name, body, 108f), M + 150f, base, body)
                cv.drawText(fit(r.details, body, 196f), M + 262f, base, body)
                cv.drawText(r.duration, M + 462f, base, paint(face.bold, 9f, DARK))
                ctx.y += 22f
            }

            ctx.finish()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
