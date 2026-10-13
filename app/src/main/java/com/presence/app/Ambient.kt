package com.presence.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/**
 * Quiet background sound for a training, made in code (no sound files) and looped.
 * It plays on its own audio track, so the voice, the vibration and the bell are never touched by it.
 */
object Ambient {
    private const val RATE = 22050
    private const val LOOP_SEC = 8
    private val handler = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()
    private val lock = Any()

    private var main: AudioTrack? = null
    private var mainGen = 0
    private var preview: AudioTrack? = null
    private var previewGen = 0

    /** key to label, in the order the picker shows them. */
    val SOUNDS = listOf(
        "off" to "Off",
        "rain" to "Rain",
        "ocean" to "Ocean waves",
        "wind" to "Soft wind",
        "brown" to "Deep calm"
    )

    fun selected(): String {
        val s = Session.prefs.getString("ambient", "off") ?: "off"
        return if (SOUNDS.any { it.first == s }) s else "off"
    }

    fun setSelected(key: String) {
        Session.prefs.edit().putString("ambient", key).apply()
    }

    /** Starts the chosen sound for a training. [volume] is 0..1. Does nothing when the sound is Off. */
    fun start(volume: Float) {
        stop()
        val kind = selected()
        if (kind == "off") return
        val gen: Int
        synchronized(lock) { gen = ++mainGen }
        Thread {
            val track = build(kind, volume) ?: return@Thread
            synchronized(lock) {
                if (gen != mainGen) {
                    release(track)
                    return@Thread
                }
                main = track
            }
            try {
                track.play()
            } catch (e: Exception) {
            }
        }.start()
    }

    /** Stops the training's background sound. Safe to call at any time. */
    fun stop() {
        val t: AudioTrack?
        synchronized(lock) {
            mainGen++
            t = main
            main = null
        }
        t?.let { release(it) }
    }

    /** Plays one sound for a few seconds so it can be heard before choosing. Does not touch the training's own sound. */
    fun preview(kind: String, ms: Long = 4000L) {
        stopPreview()
        if (kind == "off") return
        val gen: Int
        synchronized(lock) { gen = ++previewGen }
        Thread {
            val track = build(kind, 0.5f) ?: return@Thread
            synchronized(lock) {
                if (gen != previewGen) {
                    release(track)
                    return@Thread
                }
                preview = track
            }
            try {
                track.play()
            } catch (e: Exception) {
            }
            handler.postDelayed({ if (gen == previewGen) stopPreview() }, ms)
        }.start()
    }

    fun stopPreview() {
        val t: AudioTrack?
        synchronized(lock) {
            previewGen++
            t = preview
            preview = null
        }
        t?.let { release(it) }
    }

    private fun release(t: AudioTrack) {
        try {
            t.stop()
        } catch (e: Exception) {
        }
        try {
            t.release()
        } catch (e: Exception) {
        }
    }

    private fun build(kind: String, volume: Float): AudioTrack? {
        return try {
            val data = make(kind)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
            track.setLoopPoints(0, data.size, -1)
            track.setVolume(volume.coerceIn(0f, 1f))
            track
        } catch (e: Exception) {
            null
        }
    }

    /** One seamless 8 second loop. The start and end are cross-faded so the loop point cannot be heard. */
    private fun make(kind: String): ShortArray {
        synchronized(lock) { cache[kind]?.let { return it } }
        val n = RATE * LOOP_SEC
        val fade = RATE / 2
        val total = n + fade
        val rnd = java.util.Random(kind.hashCode().toLong())
        val buf = DoubleArray(total)
        var lp = 0.0      // low-pass state
        var br = 0.0      // brown-noise state
        var drop = 0
        for (i in 0 until total) {
            val t = i.toDouble() / RATE
            val w = rnd.nextDouble() * 2 - 1
            buf[i] = when (kind) {
                "rain" -> {
                    // hiss (white noise with the lows taken out) plus small drops now and then
                    lp += 0.08 * (w - lp)
                    var v = (w - lp) * 0.35
                    if (drop <= 0) drop = 300 + rnd.nextInt(2200)
                    drop--
                    if (drop in 0..90) {
                        val dt = (90 - drop) / RATE.toDouble()
                        v += Math.sin(2.0 * Math.PI * (1800 + (kind.hashCode() and 1023) * 2.0 + drop * 20.0) * dt) * Math.exp(-dt * 260.0) * 0.12
                    }
                    v
                }
                "ocean" -> {
                    // slow brown noise rising and falling like a wave, with a little foam on the crest
                    br = (br + 0.02 * w) / 1.02
                    val swell = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * t / LOOP_SEC * 2.0 - 1.2)
                    lp += 0.15 * (w - lp)
                    val foam = (w - lp) * 0.05 * swell * swell
                    br * 7.0 * swell + foam
                }
                "wind" -> {
                    // noise whose brightness drifts slowly, like gusts
                    val gust = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * t / LOOP_SEC * 3.0)
                    val a = 0.012 + 0.035 * gust
                    lp += a * (w - lp)
                    lp * (4.0 + 3.0 * gust)
                }
                else -> {
                    // brown noise: a steady, deep, calm rumble
                    br = (br + 0.02 * w) / 1.02
                    br * 8.0
                }
            }
        }
        // loop: fade the tail into the head
        val out = DoubleArray(n)
        for (i in 0 until n) out[i] = buf[i]
        for (i in 0 until fade) {
            val x = i.toDouble() / fade
            out[i] = buf[i] * Math.sin(x * Math.PI / 2) + buf[n + i] * Math.cos(x * Math.PI / 2)
        }
        var peak = 1e-9
        for (v in out) if (Math.abs(v) > peak) peak = Math.abs(v)
        val gain = 0.9 / peak * 32767.0
        val data = ShortArray(n) { (out[it] * gain).toInt().toShort() }
        synchronized(lock) { cache[kind] = data }
        return data
    }
}
