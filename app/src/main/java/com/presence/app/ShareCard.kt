package com.presence.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * The share cards: a picture drawn in code, then handed to the phone's share sheet. They show only
 * numbers and the word Presence. No name, email, dates or training names.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350
    private val BG = Color.parseColor("#0B1220")
    private val PINK = Color.parseColor("#EC4899")
    private val TEXT = Color.parseColor("#F1F5F9")
    private val MUTED = Color.parseColor("#94A3B8")
    private val TILE = Color.parseColor("#223049")
    private val INK = Color.parseColor("#0F172A")

    private fun paint(color: Int, size: Float, face: Typeface, spacing: Float = 0f): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.textSize = size
        p.typeface = face
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = spacing
        return p
    }

    fun shareStreak(ctx: Context, days: Int) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(BG)
        val cx = W / 2f
        cv.drawText("DAY STREAK", cx, 330f, paint(MUTED, 44f, Fonts.medium(ctx), 0.15f))
        val digits = days.toString()
        val size = if (digits.length >= 4) 300f else 420f
        cv.drawText(digits, cx, 720f, paint(PINK, size, Fonts.bold(ctx)))
        val line = if (days == 1) "day I showed up" else "days I showed up"
        cv.drawText(line, cx, 850f, paint(TEXT, 56f, Fonts.medium(ctx)))
        cv.drawText("Presence", cx, 1230f, paint(TEXT, 56f, Fonts.bold(ctx)))
        send(ctx, bmp, "Share my streak")
    }

    fun shareWeek(ctx: Context, s: WeekSummary) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(BG)
        val cx = W / 2f
        cv.drawText("MY WEEK", cx, 190f, paint(MUTED, 44f, Fonts.medium(ctx), 0.15f))

        val minutes = if (s.totalSec <= 0L) 0L else Math.max(1L, (s.totalSec + 30L) / 60L)
        val tileFill = Paint(Paint.ANTI_ALIAS_FLAG)
        tileFill.color = TILE
        val tiles = listOf(Pair(s.count.toString(), "Trainings"), Pair(minutes.toString(), "Minutes"))
        for ((i, t) in tiles.withIndex()) {
            val left = if (i == 0) 100f else 560f
            cv.drawRoundRect(RectF(left, 270f, left + 420f, 570f), 40f, 40f, tileFill)
            val mid = left + 210f
            cv.drawText(t.first, mid, 450f, paint(TEXT, 130f, Fonts.bold(ctx)))
            cv.drawText(t.second, mid, 530f, paint(MUTED, 44f, Fonts.medium(ctx)))
        }

        val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0..6) {
            val x = 156f + 128f * i
            val on = s.dayTrained[i]
            circle.color = if (on) PINK else TILE
            cv.drawCircle(x, 760f, 56f, circle)
            cv.drawText(s.dayLetters[i], x, 777f, paint(if (on) INK else MUTED, 48f, Fonts.bold(ctx)))
        }
        cv.drawText("${s.activeDays} of 7 days", cx, 920f, paint(TEXT, 56f, Fonts.medium(ctx)))
        cv.drawText("Presence", cx, 1230f, paint(TEXT, 56f, Fonts.bold(ctx)))
        send(ctx, bmp, "Share my week")
    }

    private fun send(ctx: Context, bmp: Bitmap, title: String) {
        try {
            val dir = File(ctx.cacheDir, "reports")
            dir.mkdirs()
            val file = File(dir, "presence-share.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, title))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't create the card", Toast.LENGTH_SHORT).show()
        }
    }
}
