package com.presence.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** A soft bell made in code (no sound file): a few gentle tones that fade out. */
object Bell {
    private val handler = Handler(Looper.getMainLooper())
    private val cache = HashMap<Boolean, ShortArray>()
    private const val RATE = 22050

    private fun make(soft: Boolean): ShortArray {
        cache[soft]?.let { return it }
        val secs = if (soft) 1.4 else 2.0
        val vol = if (soft) 0.25 else 0.4
        val n = (RATE * secs).toInt()
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val env = Math.exp(-t * 2.6)
            val attack = Math.min(1.0, t / 0.01)
            val s = Math.sin(2.0 * Math.PI * 660.0 * t) +
                0.35 * Math.sin(2.0 * Math.PI * 1320.0 * t) +
                0.12 * Math.sin(2.0 * Math.PI * 1980.0 * t)
            val v = s / 1.47 * env * attack * vol * 32767.0
            data[i] = v.toInt().toShort()
        }
        cache[soft] = data
        return data
    }

    /** Plays the bell once. Never throws: if the phone cannot play it, nothing happens. */
    fun play(soft: Boolean = false) {
        try {
            val data = make(soft)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val ms = (data.size * 1000L / RATE) + 500L
            handler.postDelayed({
                try {
                    track.release()
                } catch (e: Exception) {
                }
            }, ms)
        } catch (e: Exception) {
        }
    }
}
