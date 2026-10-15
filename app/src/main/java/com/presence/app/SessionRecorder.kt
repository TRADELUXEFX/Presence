package com.presence.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.MediaRecorder
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
 * Normally ScreenRecordService does it: your microphone mixed with the phone's own sound (the voice
 * guidance), and the screen too when "Record video" is chosen. If the screen-capture permission is refused,
 * "Record audio" falls back to the microphone alone (startAudioIfWanted).
 */
object SessionRecorder {
    private var audio: MediaRecorder? = null
    private var audioFile: File? = null
    @Volatile var serviceOn = false
    /** When the current recording began; used to match the saved file to its history entry. */
    @Volatile var startedMs = 0L

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun hasMic(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Fallback for "Record session" when the background recording was not allowed: microphone only. */
    fun startAudioIfWanted(ctx: Context) {
        if (!RecPrefs.on || serviceOn || audio != null || !hasMic(ctx)) return
        try {
            startedMs = System.currentTimeMillis()
            val f = File(ctx.cacheDir, "presence_" + stamp() + ".m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            audio = r
            audioFile = f
        } catch (e: Exception) {
            try { audio?.release() } catch (x: Exception) { }
            audio = null
            audioFile = null
        }
    }

    /** Starts the background recording: sound always, plus the screen when video is true. */
    fun startProjection(ctx: Context, resultCode: Int, data: Intent, video: Boolean) {
        try {
            val i = Intent(ctx, ScreenRecordService::class.java)
            i.putExtra("code", resultCode)
            i.putExtra("data", data)
            i.putExtra("video", video)
            serviceOn = true
            startedMs = System.currentTimeMillis()
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            serviceOn = false
        }
    }

    /** Ends whatever is recording and saves it. Safe to call when nothing is recording. */
    fun stop(ctx: Context) {
        val r = audio
        val f = audioFile
        audio = null
        audioFile = null
        if (r != null && f != null) {
            var ok = true
            try { r.stop() } catch (e: Exception) { ok = false }
            try { r.release() } catch (e: Exception) { android.util.Log.w("Presence", "SessionRecorder step failed", e) }
            if (ok) save(ctx, f, "audio/mp4", false)
        }
        if (serviceOn) {
            serviceOn = false
            try { ctx.stopService(Intent(ctx, ScreenRecordService::class.java)) } catch (e: Exception) { android.util.Log.w("Presence", "SessionRecorder step failed", e) }
        }
    }

    /** Copies a finished recording into Music/Presence or Movies/Presence so it shows up in the gallery and files app. */
    fun save(ctx: Context, src: File, mime: String, video: Boolean) {
        val app = ctx.applicationContext
        var where = ""
        var savedUri = ""
        try {
            val name = src.name
            if (Build.VERSION.SDK_INT >= 29) {
                val base = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
                val v = ContentValues()
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime)
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, base + "/Presence")
                val coll = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val uri = app.contentResolver.insert(coll, v)
                if (uri != null) {
                    app.contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(src).use { it.copyTo(out) } }
                    where = base + "/Presence"
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
                if (uid.isNotEmpty()) Recordings.add(uid, savedUri, video, startedMs, System.currentTimeMillis())
            } catch (e: Exception) { android.util.Log.w("Presence", "SessionRecorder step failed", e) }
        }
        val msg = if (where.isNotEmpty()) "Training recording saved in " + where else "Couldn't save the training recording."
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
    }
}
