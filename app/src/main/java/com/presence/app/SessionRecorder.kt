package com.presence.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records a Mirror training (No Blinking or No Fidgeting), from the start to the last rep.
 * ScreenRecordService does it: the screen plus your microphone mixed with the phone's own sound (the voice
 * guidance), saved as one video. There is no audio-only recording: if screen capture is refused, nothing is recorded.
 */
object SessionRecorder {
    @Volatile var serviceOn = false
    /** When the current recording began; used to match the saved file to its history entry. */
    @Volatile var startedMs = 0L

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun hasMic(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts the background recording: the screen with its sound. */
    fun startProjection(ctx: Context, resultCode: Int, data: Intent) {
        try {
            val i = Intent(ctx, ScreenRecordService::class.java)
            i.putExtra("code", resultCode)
            i.putExtra("data", data)
            serviceOn = true
            startedMs = System.currentTimeMillis()
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            serviceOn = false
        }
    }

    /** Ends the screen recording and saves it. Safe to call when nothing is recording. */
    fun stop(ctx: Context) {
        if (serviceOn) {
            serviceOn = false
            try { ctx.stopService(Intent(ctx, ScreenRecordService::class.java)) } catch (e: Exception) { android.util.Log.w("Presence", "SessionRecorder step failed", e) }
        }
    }

    /** Copies a finished recording into Movies/Presence (or the app's files folder on older phones) so it shows up in the gallery. */
    fun save(ctx: Context, src: File) {
        val app = ctx.applicationContext
        var where = ""
        var savedUri = ""
        try {
            val name = src.name
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Presence")
                val uri = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
                if (uri != null) {
                    app.contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(src).use { it.copyTo(out) } }
                    where = Environment.DIRECTORY_MOVIES + "/Presence"
                    savedUri = uri.toString()
                }
            } else {
                // older phones: kept in the app's own folder (no storage permission needed)
                val dir = app.getExternalFilesDir(null)
                if (dir != null) {
                    val dest = File(dir, name)
                    FileInputStream(src).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    where = "this app's files folder"
                    savedUri = Uri.fromFile(dest).toString()
                }
            }
        } catch (e: Exception) {
            where = ""
        }
        try { src.delete() } catch (e: Exception) { android.util.Log.w("Presence", "SessionRecorder step failed", e) }
        if (savedUri.isNotEmpty()) {
            try {
                val uid = Session.userId
                if (uid.isNotEmpty()) Recordings.add(uid, savedUri, startedMs, System.currentTimeMillis())
            } catch (e: Exception) { android.util.Log.w("Presence", "SessionRecorder step failed", e) }
        }
        val msg = if (where.isNotEmpty()) "Training recording saved in " + where else "Couldn't save the training recording."
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
    }
}
