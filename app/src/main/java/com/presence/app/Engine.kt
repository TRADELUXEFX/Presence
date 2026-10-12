package com.presence.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The running training: a small state machine on the main thread. It works from timestamps, so it
 * stays on time while the screen is off (TrainingService keeps the CPU awake). The training screen only
 * draws what Engine reports. Timings and the saved values match the Presence website.
 */
object Engine {
    interface Listener {
        fun onEngine()
    }

    private enum class Stage { IDLE, REP, BREATH, MEDITATION, PAUSE, REST, DONE }
    private enum class After { NEXT_BREATH_PHASE, REP_DONE }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private var appCtx: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    var config = Config(Trainings.BLINK, "open", 30, 5, 5, 10, true)
        private set
    var running = false
        private set
    var totalReps = 0
        private set
    var doneReps = 0
        private set
    var resultSec = 0
        private set

    // What the training screen draws. Refreshed on every tick.
    var ringFraction = 1f
        private set
    var ringText = ""
        private set
    var ringUnit = ""
        private set
    var badgeText = ""
        private set
    var instrText = ""
        private set
    var ringColor = Ui.BLUE
        private set
    var motionPercent = 0
        private set
    var motionText = "Still"
        private set

    private var stage = Stage.IDLE
    private var after = After.REP_DONE
    private var curRep = 0
    private var segStart = 0L
    private var segSec = 0
    private var pauseStart = 0L
    private var restStart = 0L
    private var breathPhase = 0
    private var halfSpoken = false
    private var nextCueSec = 30
    private var cueIdx = 0
    private var motion = 0f

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            tick(System.currentTimeMillis())
            if (running) handler.postDelayed(this, 100L)
        }
    }

    // ---------- public API ----------

    /** Starts a training. The caller has already checked permissions. */
    fun start(ctx: Context, cfg: Config) {
        if (running) return
        val app = ctx.applicationContext
        appCtx = app
        config = cfg
        running = true
        stage = Stage.IDLE
        curRep = 0
        doneReps = 0
        totalReps = if (cfg.key == Trainings.MEDITATE) 1 else cfg.sets
        resultSec = 0
        motion = 0f
        setMotion()
        ensureTts(app)
        TrainingService.start(app)
        beginCurrent()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        notifyListeners()
    }

    /** Ends the training without saving it. */
    fun cancel() {
        running = false
        stage = Stage.IDLE
        handler.removeCallbacks(ticker)
        tts?.stop()
        pendingSpeech = null
        appCtx?.let { TrainingService.stop(it) }
        notifyListeners()
    }

    /** Clears a finished training once its result has been shown. */
    fun reset() {
        running = false
        stage = Stage.IDLE
        handler.removeCallbacks(ticker)
        notifyListeners()
    }

    /** A screen touch. Fidget counts it as movement, as the website does. */
    fun touch() {
        if (!running || config.key != Trainings.FIDGET) return
        motion = Math.min(100f, motion + 30f)
        setMotion()
        notifyListeners()
    }

    fun isActive(): Boolean = running || stage == Stage.DONE

    fun isDone(): Boolean = stage == Stage.DONE

    fun progressLabel(): String {
        val shown = Math.min(curRep + 1, totalReps)
        return when (config.key) {
            Trainings.MEDITATE -> "Meditation"
            Trainings.BREATH -> "Cycle $shown of $totalReps"
            else -> "Rep $shown of $totalReps"
        }
    }

    /** Changes only when the training moves on, so the notification updates no more often than needed. */
    fun statusKey(): String = "$stage|$curRep|$doneReps|$breathPhase"

    fun statusText(): String {
        val shown = Math.min(curRep + 1, totalReps)
        return when (stage) {
            Stage.REP -> "Rep $shown of $totalReps"
            Stage.BREATH -> "Cycle $shown of $totalReps · " + Trainings.BREATH_PHASES[breathPhase].label
            Stage.MEDITATION -> "Meditating"
            Stage.REST -> "Rest"
            Stage.PAUSE -> "Training"
            Stage.DONE -> "Training complete"
            Stage.IDLE -> "Starting"
        }
    }

    fun addListener(l: Listener) {
        listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    // ---------- timing ----------

    private fun tick(now: Long) {
        when (stage) {
            Stage.REP -> {
                val el = ((now - segStart) / 1000L).toInt()
                if (!halfSpoken && el >= segSec / 2) {
                    halfSpoken = true
                    speak("Halfway")
                }
                decayMotion()
                showRep(Math.max(0, segSec - el))
                if (el >= segSec) enterPause(After.REP_DONE)
            }
            Stage.BREATH -> {
                val el = ((now - segStart) / 1000L).toInt()
                showBreath(Math.max(0, segSec - el))
                if (el >= segSec) enterPause(After.NEXT_BREATH_PHASE)
            }
            Stage.MEDITATION -> {
                val el = ((now - segStart) / 1000L).toInt()
                if (!halfSpoken && el >= segSec / 2) {
                    halfSpoken = true
                    speak("Halfway through your meditation. Keep going.")
                }
                if (el >= nextCueSec) {
                    speak(Trainings.MED_CUES[cueIdx % Trainings.MED_CUES.size])
                    cueIdx++
                    nextCueSec += 30
                }
                showMeditation(Math.max(0, segSec - el))
                if (el >= segSec) {
                    speak("Training complete. Great job.")
                    enterPause(After.REP_DONE)
                }
            }
            Stage.PAUSE -> if (now - pauseStart >= 1000L) afterPause()
            Stage.REST -> if (now - restStart >= config.restSec * 1000L) beginCurrent()
            else -> {
            }
        }
        notifyListeners()
    }

    private fun beginCurrent() {
        when (config.key) {
            Trainings.BREATH -> beginBreathPhase(0)
            Trainings.MEDITATE -> beginMeditation()
            else -> beginRep()
        }
    }

    private fun beginRep() {
        stage = Stage.REP
        segStart = System.currentTimeMillis()
        segSec = config.repSec
        halfSpoken = false
        motion = 0f
        setMotion()
        speak(if (config.key == Trainings.BLINK) "Don't blink" else "Don't move")
    }

    private fun beginBreathPhase(i: Int) {
        breathPhase = i
        stage = Stage.BREATH
        segStart = System.currentTimeMillis()
        segSec = 4
        speak(Trainings.BREATH_PHASES[i].label)
    }

    private fun beginMeditation() {
        stage = Stage.MEDITATION
        segStart = System.currentTimeMillis()
        segSec = config.medMin * 60
        halfSpoken = false
        nextCueSec = 30
        cueIdx = 0
        speak("Begin your meditation. Breathe slowly and relax.")
    }

    /** A short hold at zero before the next step, as on the website. */
    private fun enterPause(next: After) {
        stage = Stage.PAUSE
        after = next
        pauseStart = System.currentTimeMillis()
        ringFraction = 1f
        ringText = if (config.key == Trainings.MEDITATE) "0:00" else "0"
    }

    private fun afterPause() {
        when (after) {
            After.NEXT_BREATH_PHASE -> {
                breathPhase++
                if (breathPhase < Trainings.BREATH_PHASES.size) beginBreathPhase(breathPhase) else repDone()
            }
            After.REP_DONE -> repDone()
        }
    }

    private fun repDone() {
        doneReps++
        curRep++
        if (curRep >= totalReps) {
            finish()
            return
        }
        speak("Rest")
        stage = Stage.REST
        restStart = System.currentTimeMillis()
        ringFraction = 1f
        ringText = ""
        ringUnit = ""
        badgeText = ""
        instrText = "Rest. Breathe. Prepare for next rep."
        ringColor = Trainings.accent(config.key)
    }

    private fun finish() {
        val c = config
        val sec = Trainings.totalSec(c)
        stage = Stage.DONE
        running = false
        resultSec = sec
        handler.removeCallbacks(ticker)
        if (c.key != Trainings.MEDITATE) speak("Training complete. Great job.")
        val uid = Session.userId
        if (uid.isNotEmpty()) {
            LocalStore.recordTraining(uid, c.key, sec, Trainings.meta(c))
            Sync.flush()
        }
        appCtx?.let { TrainingService.stop(it) }
        notifyListeners()
    }

    // ---------- what the screen shows ----------

    private fun showRep(rem: Int) {
        ringFraction = (segSec - rem).toFloat() / segSec
        ringText = rem.toString()
        ringUnit = "seconds"
        ringColor = Trainings.accent(config.key)
        badgeText = if (config.key == Trainings.BLINK) "Hold Gaze" else "Be Still"
        instrText = Trainings.instruction(config.key, config.mode)
    }

    private fun showBreath(rem: Int) {
        val ph = Trainings.BREATH_PHASES[breathPhase]
        ringFraction = (segSec - rem).toFloat() / segSec
        ringText = rem.toString()
        ringUnit = "counts"
        ringColor = ph.color
        badgeText = ph.label
        instrText = ph.instr
    }

    private fun showMeditation(rem: Int) {
        ringFraction = (segSec - rem).toFloat() / segSec
        ringText = mmss(rem)
        ringUnit = "remaining"
        ringColor = Trainings.accent(Trainings.MEDITATE)
        badgeText = "Meditating"
        instrText = "Sit still. Breathe naturally. Let thoughts pass like clouds."
    }

    private fun mmss(s: Int): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

    private fun decayMotion() {
        motion = Math.max(0f, motion - 2f)
        setMotion()
    }

    private fun setMotion() {
        motionPercent = Math.round(motion)
        motionText = if (motion < 5f) "Still" else "${Math.round(motion)}%"
    }

    // ---------- voice ----------

    private fun ensureTts(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                var r = t.setLanguage(Locale.getDefault())
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    r = t.setLanguage(Locale.US)
                }
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                val p = pendingSpeech
                pendingSpeech = null
                if (p != null && ttsReady) speak(p)
            }
        }
    }

    private fun speak(text: String) {
        if (!config.voice) return
        val t = tts
        if (t == null || !ttsReady) {
            pendingSpeech = text
            return
        }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "presence-" + System.nanoTime())
    }

    private fun notifyListeners() {
        for (l in listeners) l.onEngine()
    }
}
