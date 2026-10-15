package com.presence.app

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Records the sound of a whole session as one track: your microphone mixed with the phone's own sound
 * (the voice guidance). While the phone is speaking the microphone is left out, so the voice isn't doubled. The phone's sound comes from Android's playback capture, which needs the
 * screen-capture permission; without it (or before Android 10) only the microphone is recorded.
 */
class SessionAudio(private val projection: MediaProjection?, private val micMuted: () -> Boolean = { false }) {
    private val rate = 44100
    private val frame = 2048
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile var ok = false
        private set
    /** True when the phone's own sound is part of the recording. */
    @Volatile var hasPlayback = false
        private set

    @SuppressLint("MissingPermission")
    fun start(out: File) {
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val buf = Math.max(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192) * 2
        val mic = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(buf)
            .build()
        var play: AudioRecord? = null
        if (Build.VERSION.SDK_INT >= 29 && projection != null) {
            try {
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val p = AudioRecord.Builder()
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(buf)
                    .setAudioPlaybackCaptureConfig(cfg)
                    .build()
                if (p.state == AudioRecord.STATE_INITIALIZED) play = p else p.release()
            } catch (e: Exception) {
                play = null
            }
        }
        hasPlayback = play != null

        val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1)
        aac.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        aac.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
        aac.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        running = true
        val playRec = play
        val t = Thread {
            var track = -1
            var started = false
            val info = MediaCodec.BufferInfo()
            var total = 0L
            var wrote = false

            fun drain(untilEnd: Boolean) {
                var spins = 0
                while (true) {
                    val i = codec.dequeueOutputBuffer(info, if (untilEnd) 10000L else 0L)
                    if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    } else if (i >= 0) {
                        val data = codec.getOutputBuffer(i)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (data != null && info.size > 0 && started) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                            wrote = true
                        }
                        codec.releaseOutputBuffer(i, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    } else if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!untilEnd) return
                        spins++
                        if (spins > 100) return
                    }
                }
            }

            try {
                mic.startRecording()
                playRec?.startRecording()
                val a = ShortArray(frame)
                val b = ShortArray(frame)
                val bytes = ByteArray(frame * 2)
                while (running) {
                    val n = mic.read(a, 0, frame, AudioRecord.READ_BLOCKING)
                    if (n <= 0) break
                    var m = 0
                    if (playRec != null) {
                        m = Math.max(0, playRec.read(b, 0, n, AudioRecord.READ_BLOCKING))
                    }
                    // the phone's voice is already recorded cleanly, so leave the microphone out while it speaks
                    // (otherwise the mic hears it from the speaker too and it sounds like an echo)
                    val muteMic = playRec != null && micMuted()
                    for (k in 0 until n) {
                        var v = if (muteMic) 0 else a[k].toInt()
                        if (k < m) v += b[k].toInt()
                        if (v > 32767) v = 32767
                        if (v < -32768) v = -32768
                        bytes[2 * k] = (v and 0xFF).toByte()
                        bytes[2 * k + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    val idx = codec.dequeueInputBuffer(10000L)
                    if (idx >= 0) {
                        val ib: ByteBuffer? = codec.getInputBuffer(idx)
                        if (ib != null) {
                            ib.clear()
                            ib.put(bytes, 0, n * 2)
                            codec.queueInputBuffer(idx, 0, n * 2, total * 1000000L / rate, 0)
                        }
                    }
                    total += n
                    drain(false)
                }
                val idx = codec.dequeueInputBuffer(20000L)
                if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, total * 1000000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
            } catch (e: Exception) {
                // whatever was written so far is kept
            }
            try { mic.stop() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
            try { playRec?.stop() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
            mic.release()
            playRec?.release()
            try { codec.stop() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
            try { codec.release() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
            try { if (started) muxer.stop() } catch (e: Exception) { wrote = false }
            try { muxer.release() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
            ok = wrote
        }
        thread = t
        t.start()
    }

    /** Stops and waits for the file to be finished. */
    fun stop() {
        running = false
        try { thread?.join(3000L) } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
        thread = null
    }

    companion object {
        /** Joins the screen video (no sound) and the sound track into one mp4. */
        fun mux(video: File, audio: File, out: File): Boolean {
            var muxer: MediaMuxer? = null
            val vEx = MediaExtractor()
            val aEx = MediaExtractor()
            try {
                vEx.setDataSource(video.absolutePath)
                aEx.setDataSource(audio.absolutePath)
                muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var vOut = -1
                var aOut = -1
                for (i in 0 until vEx.trackCount) {
                    val f = vEx.getTrackFormat(i)
                    if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                        vEx.selectTrack(i); vOut = muxer.addTrack(f); break
                    }
                }
                for (i in 0 until aEx.trackCount) {
                    val f = aEx.getTrackFormat(i)
                    if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                        aEx.selectTrack(i); aOut = muxer.addTrack(f); break
                    }
                }
                if (vOut < 0 || aOut < 0) return false
                muxer.start()
                val buf = ByteBuffer.allocate(1 shl 20)
                val info = MediaCodec.BufferInfo()
                for (pair in listOf(Pair(vEx, vOut), Pair(aEx, aOut))) {
                    val ex = pair.first
                    while (true) {
                        buf.clear()
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = ex.sampleTime
                        info.flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        muxer.writeSampleData(pair.second, buf, info)
                        ex.advance()
                    }
                }
                muxer.stop()
                return true
            } catch (e: Exception) {
                return false
            } finally {
                try { muxer?.release() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
                try { vEx.release() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
                try { aEx.release() } catch (e: Exception) { android.util.Log.w("Presence", "SessionAudio step failed", e) }
            }
        }
    }
}
