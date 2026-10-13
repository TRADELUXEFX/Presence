package com.presence.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    /**
     * True during the 5 second gap between two trainings of a session. The Engine itself starts the next
     * training when the gap ends, so a session carries on with the app closed or the phone locked. The
     * background service stays up through the gap, which is what lets the next training start.
     */
    private var sessionPending = false
    private val nextRunnable = Runnable { runNext() }

    /** Gap between two trainings of a session. */
    const val NEXT_DELAY_MS = 5000L

    /** When the next training of the session will start (SystemClock.uptimeMillis), so the Result screen can count down in step. */
    var nextAt = 0L
        private set

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
        handler.removeCallbacks(nextRunnable)
        sessionPending = false
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
        // Between two trainings of a session the service is already running. Android does not allow a
        // background app to start one, so it is only started when it is not up.
        if (!TrainingService.up) TrainingService.start(app)
        Dnd.engage(app)
        // background sound sits quietly under the training; lower still when the voice is speaking cues
        Ambient.start(if (cfg.voice) 0.3f else 0.5f)
        chime(false)
        beginCurrent()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        notifyListeners()
    }

    /** Ends the training without saving it, and ends the session it belongs to. */
    fun cancel() {
        running = false
        stage = Stage.IDLE
        sessionPending = false
        handler.removeCallbacks(nextRunnable)
        TrainingSession.clear()
        handler.removeCallbacks(ticker)
        Ambient.stop()
        tts?.stop()
        pendingSpeech = null
        appCtx?.let {
            TrainingService.stop(it)
            Dnd.restore(it)
        }
        notifyListeners()
    }

    /** Clears a finished training once its result has been shown. */
    fun reset() {
        running = false
        stage = Stage.IDLE
        handler.removeCallbacks(ticker)
        Ambient.stop()
        appCtx?.let { Dnd.restore(it) }
        notifyListeners()
    }

    /** Movement from the phone's motion sensor. Fidget only, while a training runs. */
    fun addMotion(amount: Float) {
        if (!running || config.key != Trainings.FIDGET) return
        motion = Math.min(100f, motion + amount)
        setMotion()
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

    /** The background service stays up while a training runs, and through the gap before the next one of a session. */
    fun keepService(): Boolean = isActive() || sessionPending

    fun isDone(): Boolean = stage == Stage.DONE

    /** The person left the session: no next training will start. */
    fun cancelSession() {
        handler.removeCallbacks(nextRunnable)
        if (!sessionPending) return
        sessionPending = false
        if (!running) appCtx?.let { TrainingService.stop(it) }
        notifyListeners()
    }

    private fun runNext(): Boolean {
        handler.removeCallbacks(nextRunnable)
        if (!sessionPending) return false
        sessionPending = false
        val app = appCtx
        val nxt = TrainingSession.next()
        if (app == null || nxt == null) {
            app?.let { TrainingService.stop(it) }
            notifyListeners()
            return false
        }
        start(app, nxt)
        return running
    }

    fun progressLabel(): String {
        val shown = Math.min(curRep + 1, totalReps)
        return when (config.key) {
            Trainings.MEDITATE -> "Meditation"
            Trainings.BREATH -> "Cycle $shown of $totalReps"
            else -> "Rep $shown of $totalReps"
        }
    }

    /** Changes only when the training moves on, so the notification updates no more often than needed. */
    fun statusKey(): String = "$stage|$curRep|$doneReps|$breathPhase|$sessionPending"

    fun statusText(): String {
        if (sessionPending && !running) return "Next training starting"
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
        buzz(phaseBuzz(i))
        speak(Trainings.BREATH_PHASES[i].label, false)
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
        instrText = "Rest and breathe to prepare for the next rep"
        ringColor = Trainings.accent(config.key)
    }

    private fun finish() {
        val c = config
        val sec = Trainings.totalSec(c)
        stage = Stage.DONE
        running = false
        resultSec = sec
        handler.removeCallbacks(ticker)
        Ambient.stop()
        chime(false)
        if (c.key != Trainings.MEDITATE) speak("Training complete. Great job.")
        val uid = Session.userId
        if (uid.isNotEmpty()) {
            val row = LocalStore.recordTraining(uid, c.key, sec, Trainings.meta(c))
            if (TrainingSession.items.size > 1) SessionLog.put(uid, row.optLong("ts"), TrainingSession.name, TrainingSession.index + 1, TrainingSession.items.size)
            if (TrainingSession.items.size <= 1) Recent.remember(uid, c)
            Sync.flush()
        }
        // More trainings in this session: keep the background service up and start the next one in 5 seconds,
        // whether or not the app is on screen.
        val continuing = uid.isNotEmpty() && TrainingSession.items.size > 1 && TrainingSession.hasNext()
        sessionPending = continuing
        appCtx?.let {
            if (!continuing) TrainingService.stop(it)
            Dnd.restore(it)
            StreakWidget.updateAll(it)
        }
        handler.removeCallbacks(nextRunnable)
        if (continuing) {
            nextAt = SystemClock.uptimeMillis() + NEXT_DELAY_MS
            handler.postDelayed(nextRunnable, NEXT_DELAY_MS)
        }
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

    /** Box Breathing: true while the breathing phases are running. */
    fun breathing(): Boolean = stage == Stage.BREATH

    /** Box Breathing: how full the breathing circle is now, from 0.45 (small) to 1 (full). */
    fun breathScale(): Float {
        val f = ringFraction
        return when (breathPhase) {
            0 -> 0.45f + 0.55f * f
            1 -> 1f
            2 -> 1f - 0.55f * f
            else -> 0.45f
        }
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
        instrText = "Sit still, breathe naturally and let thoughts pass like clouds"
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

    /** A short buzz with each cue, if vibration is on in settings. */
    private fun buzz(pattern: LongArray = longArrayOf(0L, 60L)) {
        val ctx = appCtx ?: return
        if (!Session.prefs.getBoolean("vibrate", true)) return
        val vib = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } else {
            vib.vibrate(pattern, -1)
        }
    }

    /** Box Breathing: each phase feels different. Inhale one pulse, hold two light taps, exhale one long pulse. */
    private fun phaseBuzz(i: Int): LongArray = when (i) {
        0 -> longArrayOf(0L, 90L)
        2 -> longArrayOf(0L, 220L)
        else -> longArrayOf(0L, 40L, 70L, 40L)
    }

    /** The soft bell, if sound is on in settings. */
    private fun chime(soft: Boolean) {
        if (!Session.prefs.getBoolean("bell", true)) return
        Bell.play(soft)
    }

    private fun speak(text: String, shake: Boolean = true) {
        if (shake) buzz()
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
