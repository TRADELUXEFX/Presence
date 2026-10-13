package com.presence.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Bells made in code (no sound files): a few gentle tones that fade out. The style is chosen on the training screen. */
object Bell {
    private val handler = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()
    private const val RATE = 22050

    /** key to label, in the order the picker shows them. */
    val STYLES = listOf(
        "soft" to "Soft bell",
        "bowl" to "Singing bowl",
        "chime" to "Chime",
        "wood" to "Wood block"
    )

    fun style(): String {
        val s = Session.prefs.getString("bell_style", "soft") ?: "soft"
        return if (STYLES.any { it.first == s }) s else "soft"
    }

    fun setStyle(key: String) {
        Session.prefs.edit().putString("bell_style", key).apply()
    }

    private fun make(style: String, soft: Boolean): ShortArray {
        val ck = "$style-$soft"
        cache[ck]?.let { return it }
        // seconds and volume for the full-size bell; the soft one is shorter and quieter
        val (secs0, vol0) = when (style) {
            "bowl" -> 3.6 to 0.38
            "chime" -> 2.4 to 0.30
            "wood" -> 0.35 to 0.55
            else -> 2.0 to 0.40
        }
        val secs = if (soft) secs0 * 0.7 else secs0
        val vol = if (soft) vol0 * 0.625 else vol0
        val n = (RATE * secs).toInt()
        val data = ShortArray(n)
        val rnd = java.util.Random(7)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val tau = 2.0 * Math.PI * t
            val v: Double = when (style) {
                "bowl" -> {
                    // a low tone with two close notes that beat slowly, plus the bowl's uneven overtones
                    val env = Math.exp(-t * 1.1)
                    val attack = Math.min(1.0, t / 0.06)
                    val s = Math.sin(tau * 196.0) + Math.sin(tau * 198.0) +
                        0.45 * Math.sin(tau * 196.0 * 2.71) + 0.2 * Math.sin(tau * 196.0 * 5.15)
                    s / 2.9 * env * attack
                }
                "chime" -> {
                    // bright and short, with a shimmer
                    val env = Math.exp(-t * 1.9)
                    val attack = Math.min(1.0, t / 0.002)
                    val s = Math.sin(tau * 1568.0) + 0.5 * Math.sin(tau * 1568.0 * 2.76) +
                        0.25 * Math.sin(tau * 1568.0 * 5.4) + 0.5 * Math.sin(tau * 2350.0)
                    s / 2.25 * env * attack
                }
                "wood" -> {
                    // a quick hollow knock
                    val env = Math.exp(-t * 22.0)
                    val attack = Math.min(1.0, t / 0.001)
                    val s = Math.sin(tau * 880.0) + 0.5 * Math.sin(tau * 1320.0) + 0.3 * (rnd.nextDouble() * 2 - 1) * Math.exp(-t * 90.0)
                    s / 1.8 * env * attack
                }
                else -> {
                    val env = Math.exp(-t * 2.6)
                    val attack = Math.min(1.0, t / 0.01)
                    val s = Math.sin(tau * 660.0) + 0.35 * Math.sin(tau * 1320.0) + 0.12 * Math.sin(tau * 1980.0)
                    s / 1.47 * env * attack
                }
            }
            data[i] = (v * vol * 32767.0).toInt().toShort()
        }
        cache[ck] = data
        return data
    }

    /** Plays the bell once, in the chosen style. Never throws: if the phone cannot play it, nothing happens. */
    fun play(soft: Boolean = false, style: String = style()) {
        try {
            val data = make(style, soft)
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
