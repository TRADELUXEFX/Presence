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
    private const val LOOP_SEC = 60
    private val handler = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()
    private const val CHUNK = 2048
    private val lock = Any()

    /** A track fed continuously by its own thread, so the loop never has a gap at the seam. */
    private class Voice(val track: AudioTrack, val data: ShortArray) {
        @Volatile var alive = true
    }

    private var main: Voice? = null
    private var mainGen = 0
    private var preview: Voice? = null
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
            val voice = build(kind, volume) ?: return@Thread
            synchronized(lock) {
                if (gen != mainGen) {
                    release(voice)
                    return@Thread
                }
                main = voice
            }
            playLoop(voice)
        }.start()
    }

    /** Stops the training's background sound. Safe to call at any time. */
    fun stop() {
        val t: Voice?
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
            val voice = build(kind, 0.5f) ?: return@Thread
            synchronized(lock) {
                if (gen != previewGen) {
                    release(voice)
                    return@Thread
                }
                preview = voice
            }
            handler.postDelayed({ if (gen == previewGen) stopPreview() }, ms)
            playLoop(voice)
        }.start()
    }

    fun stopPreview() {
        val t: Voice?
        synchronized(lock) {
            previewGen++
            t = preview
            preview = null
        }
        t?.let { release(it) }
    }

    /** Plays the loop over and over on this thread until the voice is stopped, then frees the track. */
    private fun playLoop(v: Voice) {
        try {
            v.track.play()
            var i = 0
            while (v.alive) {
                val len = Math.min(CHUNK, v.data.size - i)
                val w = v.track.write(v.data, i, len, AudioTrack.WRITE_NON_BLOCKING)
                if (w < 0) break
                if (w == 0) {
                    // the buffer is full: wait a moment for it to drain
                    try { Thread.sleep(15) } catch (e: InterruptedException) { break }
                } else {
                    i = (i + w) % v.data.size
                }
            }
        } catch (e: Exception) {
        } finally {
            try { v.track.stop() } catch (e: Exception) {}
            try { v.track.release() } catch (e: Exception) {}
        }
    }

    /** Asks the voice to stop; its own thread frees the track. If it never started playing, free it here. */
    private fun release(v: Voice) {
        v.alive = false
        if (v.track.playState == AudioTrack.PLAYSTATE_STOPPED && v.track.state == AudioTrack.STATE_INITIALIZED && !startedOrRunning(v)) {
            try { v.track.release() } catch (e: Exception) {}
        }
    }

    private fun startedOrRunning(v: Voice): Boolean = v.track.playState != AudioTrack.PLAYSTATE_STOPPED

    private fun build(kind: String, volume: Float): Voice? {
        return try {
            val data = make(kind)
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
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
                // about a second of sound queued, so a busy phone never starves it
                .setBufferSizeInBytes(Math.max(min, RATE * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.setVolume(volume.coerceIn(0f, 1f))
            Voice(track, data)
        } catch (e: Exception) {
            null
        }
    }

    /** One seamless 60 second loop. The start and end are cross-faded so the loop point cannot be heard. */
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
                    // slow waves: a 10 second one with a 15 second one drifting through it, so the pattern takes the
                    // whole minute to come round. Whole cycles fit in the loop, and the level never drops near silence.
                    val swell = 0.75 + 0.15 * Math.sin(2.0 * Math.PI * t / 10.0 - 1.2) + 0.10 * Math.sin(2.0 * Math.PI * t / 15.0 + 0.7)
                    lp += 0.15 * (w - lp)
                    val foam = (w - lp) * 0.05 * swell * swell
                    br * 7.0 * swell + foam
                }
                "wind" -> {
                    // noise whose brightness drifts slowly, like gusts
                    val gust = 0.5 + 0.3 * Math.sin(2.0 * Math.PI * t / 12.0) + 0.2 * Math.sin(2.0 * Math.PI * t / 20.0 + 1.0)
                    val a = 0.02 + 0.02 * gust
                    lp += a * (w - lp)
                    lp * (5.0 + 1.5 * gust)
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
