package com.presence.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File

/**
 * Records a Mirror training in the background: the screen plus your microphone mixed with the phone's own
 * sound, saved as one video file.
 */
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var sound: SessionAudio? = null
    private var videoFile: File? = null
    private var soundFile: File? = null
    private var finished = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNow()
        val code = intent.getIntExtra("code", 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra("data")
        }
        if (data == null || !SessionRecorder.hasMic(this)) {
            SessionRecorder.serviceOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            begin(code, data)
        } catch (e: Exception) {
            SessionRecorder.serviceOn = false
            cleanup()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val channel = "presence_record"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, "Training recording", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channel) else Notification.Builder(this)
        val n = b.setContentTitle("Recording your training")
            .setContentText("The screen and all sound are being recorded")
            .setSmallIcon(R.drawable.ic_stat_presence)
            .setColor(Ui.BLUE)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1002, n)
        }
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // newer Android versions can hand back nothing (permission already used up): stop cleanly instead of crashing
        val p = mpm.getMediaProjection(code, data) ?: run { finish(); return }
        projection = p
        // Android 14 needs this callback before recording starts; it also fires if the user stops sharing
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                finish()
            }
        }, Handler(Looper.getMainLooper()))

        val stamp = SessionRecorder.stamp()
        // sound first: your mic mixed with the phone's own sound
        val sf = File(cacheDir, "presence_sound_" + stamp + ".m4a")
        soundFile = sf
        val s = SessionAudio(p) { Engine.isVoiceActive() }
        sound = s
        s.start(sf)

        val dm = resources.displayMetrics
        var w = dm.widthPixels
        var h = dm.heightPixels
        val scale = Math.min(1f, 720f / Math.min(w, h))
        w = ((w * scale).toInt() / 2) * 2
        h = ((h * scale).toInt() / 2) * 2
        val vf = File(cacheDir, "presence_video_" + stamp + ".mp4")
        videoFile = vf
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setOutputFile(vf.absolutePath)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(w, h)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(3000000)
        r.prepare()
        recorder = r
        display = p.createVirtualDisplay("presence", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        r.start()
    }

    private fun cleanup() {
        try { display?.release() } catch (e: Exception) { android.util.Log.w("Presence", "ScreenRecordService step failed", e) }
        display = null
        try { recorder?.release() } catch (e: Exception) { android.util.Log.w("Presence", "ScreenRecordService step failed", e) }
        recorder = null
        try { projection?.stop() } catch (e: Exception) { android.util.Log.w("Presence", "ScreenRecordService step failed", e) }
        projection = null
    }

    /** Stops everything, joins sound and video, and saves the result. Runs once. */
    private fun finish() {
        if (finished) return
        finished = true
        sound?.stop()
        var videoOk = true
        try { recorder?.stop() } catch (e: Exception) { videoOk = false }
        cleanup()
        SessionRecorder.serviceOn = false
        val sf = soundFile
        val vf = videoFile
        val soundOk = sound?.ok == true && sf != null && sf.exists()
        Thread {
            if (videoOk && vf != null && vf.exists()) {
                val out = File(cacheDir, "presence_session_" + SessionRecorder.stamp() + ".mp4")
                if (soundOk && sf != null && SessionAudio.mux(vf, sf, out)) {
                    SessionRecorder.save(this, out)
                } else {
                    // joining the sound failed: keep the picture rather than lose the whole training
                    SessionRecorder.save(this, vf)
                }
            }
            sf?.delete()
            Handler(Looper.getMainLooper()).post { stopSelf() }
        }.start()
    }

    override fun onDestroy() {
        if (!finished) finish()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
