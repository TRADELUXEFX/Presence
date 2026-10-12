package com.presence.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One-page A4 PDF of the week, written to the app's cache and shared through FileProvider. */
object WeeklyReport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MAX_LINES = 30

    fun write(ctx: Context, s: Weekly.Summary): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            isFakeBoldText = true
            color = 0xFF0F172A.toInt()
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            isFakeBoldText = true
            color = 0xFF0F172A.toInt()
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            color = 0xFF0F172A.toInt()
        }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = 0xFF64748B.toInt()
        }

        val day = SimpleDateFormat("MMM d", Locale.US)
        val when24 = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US)
        val now = System.currentTimeMillis()
        var y = 56f
        canvas.drawText("Presence: weekly report", 40f, y, title)
        y += 22f
        canvas.drawText(
            "Last 7 days: ${day.format(Date(Weekly.weekStart(now)))} to ${day.format(Date(now))}",
            40f, y, muted
        )
        y += 34f
        canvas.drawText("Trainings: ${s.count}", 40f, y, body)
        y += 18f
        canvas.drawText("Total training time: ${Fmt.duration(s.totalSec)}", 40f, y, body)
        y += 18f
        canvas.drawText("Days trained: ${s.days} of 7", 40f, y, body)
        y += 26f
        for (k in Trainings.ALL) {
            canvas.drawText("${Trainings.name(k)}: ${s.perMod[k] ?: 0}", 40f, y, body)
            y += 18f
        }
        y += 14f
        canvas.drawText("Trainings this week", 40f, y, heading)
        y += 22f

        if (s.entries.isEmpty()) canvas.drawText("No trainings in this period.", 40f, y, muted)
        for (r in s.entries.take(MAX_LINES)) {
            val line = "${when24.format(Date(r.optLong("ts", 0L)))}   ${Trainings.name(r.optString("mod"))}   " +
                Fmt.duration(r.optLong("sec", 0L))
            canvas.drawText(line, 40f, y, body)
            y += 17f
        }
        if (s.entries.size > MAX_LINES) {
            canvas.drawText("And ${s.entries.size - MAX_LINES} more in the app.", 40f, y, muted)
        }

        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "presence-weekly-report.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share weekly report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
