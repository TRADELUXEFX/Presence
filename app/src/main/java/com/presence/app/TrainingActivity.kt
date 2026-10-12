package com.presence.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.presence.app.Ui.addTo

/**
 * The training screen: ring, timer, instructions, rep dots, and the finished view.
 * It only draws what Engine reports. Engine keeps running when this screen is closed.
 */
class TrainingActivity : BaseActivity(), Engine.Listener {
    private lateinit var trainingView: LinearLayout
    private lateinit var doneView: ScrollView
    private lateinit var progress: TextView
    private lateinit var camNote: TextView
    private lateinit var ring: RingView
    private lateinit var ringNum: TextView
    private lateinit var ringUnit: TextView
    private lateinit var badge: TextView
    private lateinit var instr: TextView
    private lateinit var dots: LinearLayout
    private lateinit var meter: LinearLayout
    private lateinit var meterVal: TextView
    private lateinit var meterFill: View
    private lateinit var doneTitle: TextView
    private lateinit var doneQuote: TextView
    private lateinit var doneStats: LinearLayout
    private var preview: PreviewView? = null
    private var scrimView: View? = null
    private var camProvider: ProcessCameraProvider? = null
    private var cameraOn = false
    private var showingDone = false
    private var dotCount = -1
    private var dotViews = ArrayList<View>()
    private var meterRed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Engine.isActive()) {
            finish()
            return
        }
        val key = Engine.config.key
        cameraOn = Engine.config.mode == "mirror" && (key == Trainings.BLINK || key == Trainings.FIDGET)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        build()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmLeave()
            }
        })
        if (Engine.isDone()) showDone() else render()
    }

    override fun onStart() {
        super.onStart()
        Engine.addListener(this)
        if (cameraOn && !showingDone) startCamera()
        if (!showingDone) render()
    }

    override fun onStop() {
        Engine.removeListener(this)
        try {
            camProvider?.unbindAll()
        } catch (e: Exception) {
        }
        super.onStop()
    }

    override fun onEngine() {
        if (isFinishing) return
        if (Engine.isDone()) {
            if (!showingDone) showDone()
            return
        }
        if (!Engine.isActive()) {
            finish()
            return
        }
        render()
    }

    // ---------- layout ----------

    private fun build() {
        val c = this
        val fg = if (cameraOn) Color.WHITE else Ui.TEXT
        val muted = if (cameraOn) Color.parseColor("#D9FFFFFF") else Ui.MUTED

        val frame = FrameLayout(c)
        frame.setBackgroundColor(Ui.BG)
        if (cameraOn) {
            val pv = PreviewView(c)
            pv.scaleType = PreviewView.ScaleType.FILL_CENTER
            preview = pv
            frame.addView(pv, FrameLayout.LayoutParams(-1, -1))
            val scrim = View(c)
            scrim.setBackgroundColor(Color.parseColor("#B3000000"))
            scrimView = scrim
            frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        }

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.addView(
            Ui.header(c, Trainings.name(Engine.config.key), Trainings.accent(Engine.config.key)) { confirmLeave() },
            LinearLayout.LayoutParams(-1, -2)
        )
        val content = FrameLayout(c)
        trainingView = buildTraining(c, fg, muted)
        doneView = buildDone(c, fg, muted)
        content.addView(trainingView, FrameLayout.LayoutParams(-1, -1))
        content.addView(doneView, FrameLayout.LayoutParams(-1, -1))
        column.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(column, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
    }

    private fun buildTraining(c: Context, fg: Int, muted: Int): LinearLayout {
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL
        v.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 24))
        if (Engine.config.key == Trainings.FIDGET) {
            v.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) Engine.touch()
                false
            }
        }

        progress = Ui.text(c, "", 13f, muted, Ui.MED)
        progress.gravity = Gravity.CENTER
        v.addView(progress)

        camNote = Ui.text(c, "Camera not available. Training continues without it.", 12f, Ui.AMBER, Ui.MED)
        camNote.gravity = Gravity.CENTER
        camNote.visibility = View.GONE
        v.addTo(camNote, 4)

        val ringBox = FrameLayout(c)
        ring = RingView(c)
        ring.ringColor = Trainings.accent(Engine.config.key)
        ringBox.addView(ring, FrameLayout.LayoutParams(Ui.dp(c, 250), Ui.dp(c, 250), Gravity.CENTER))
        val center = LinearLayout(c)
        center.orientation = LinearLayout.VERTICAL
        center.gravity = Gravity.CENTER
        ringNum = Ui.text(c, "", 52f, fg, Ui.BOLD)
        ringNum.gravity = Gravity.CENTER
        ringUnit = Ui.text(c, "", 13f, muted, Ui.MED)
        ringUnit.gravity = Gravity.CENTER
        center.addView(ringNum)
        center.addView(ringUnit)
        ringBox.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        v.addTo(ringBox, 10)

        badge = Ui.chip(c, "", Trainings.ink(Engine.config.key), Trainings.soft(Engine.config.key))
        badge.visibility = View.GONE
        v.addTo(badge, 14)

        instr = Ui.text(c, "", 16f, fg, Ui.MED)
        instr.gravity = Gravity.CENTER
        instr.setLineSpacing(0f, 1.25f)
        v.addTo(instr, 16)

        dots = LinearLayout(c)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        v.addTo(dots, 18)

        // Fidget: the motion meter, raised by screen touches as on the website
        meter = LinearLayout(c)
        meter.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(Ui.text(c, "Motion", 13f, muted, Ui.MED), LinearLayout.LayoutParams(0, -2, 1f))
        meterVal = Ui.text(c, "Still", 13f, fg, Ui.BOLD)
        head.addView(meterVal)
        meter.addView(head)
        val track = FrameLayout(c)
        track.background = Ui.rounded(c, Ui.SOFT, 6)
        meterFill = View(c)
        meterFill.background = Ui.rounded(c, Trainings.accent(Engine.config.key), 6)
        meterFill.pivotX = 0f
        meterFill.scaleX = 0f
        track.addView(meterFill, FrameLayout.LayoutParams(-1, -1))
        val trackLp = LinearLayout.LayoutParams(-1, Ui.dp(c, 8))
        trackLp.topMargin = Ui.dp(c, 8)
        meter.addView(track, trackLp)
        meter.visibility = if (Engine.config.key == Trainings.FIDGET) View.VISIBLE else View.GONE
        v.addTo(meter, 18)

        val stop = Ui.pill(c, "End training", Ui.SOFT, Ui.TEXT)
        stop.setOnClickListener { confirmLeave() }
        v.addTo(stop, 26)
        return v
    }

    private fun buildDone(c: Context, fg: Int, muted: Int): ScrollView {
        val sv = ScrollView(c)
        sv.isFillViewport = true
        sv.visibility = View.GONE
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL
        v.setPadding(Ui.dp(c, 22), Ui.dp(c, 28), Ui.dp(c, 22), Ui.dp(c, 28))

        doneTitle = Ui.text(c, "", 28f, fg, Ui.BOLD)
        doneTitle.gravity = Gravity.CENTER
        v.addView(doneTitle)
        doneQuote = Ui.text(c, "", 15f, muted, Ui.REG)
        doneQuote.gravity = Gravity.CENTER
        doneQuote.setLineSpacing(0f, 1.3f)
        v.addTo(doneQuote, 12)

        doneStats = Ui.card(c, Ui.CARD_TINT)
        v.addTo(doneStats, 24)

        val done = Ui.pill(c, "Done", Trainings.accent(Engine.config.key), Ui.onColor(Trainings.accent(Engine.config.key)))
        done.setOnClickListener { finishToHome() }
        v.addTo(done, 24)

        sv.addView(v)
        return sv
    }

    // ---------- drawing ----------

    /** Redraws the live values. Only changed text is set, so nothing flickers. */
    private fun render() {
        if (showingDone) return
        val e = Engine.config
        val eng = Engine
        ring.fraction = eng.ringFraction
        ring.ringColor = eng.ringColor
        setText(ringNum, eng.ringText)
        setText(ringUnit, eng.ringUnit)
        setText(badge, eng.badgeText)
        badge.visibility = if (eng.badgeText.isEmpty()) View.GONE else View.VISIBLE
        setText(instr, eng.instrText)
        setText(progress, eng.progressLabel())
        if (dotCount != eng.totalReps) buildDots(eng.totalReps)
        updateDots(eng.doneReps)
        if (e.key == Trainings.FIDGET) updateMeter(eng.motionPercent, eng.motionText)
    }

    private fun setText(tv: TextView, s: String) {
        if (tv.text.toString() != s) tv.text = s
    }

    private fun buildDots(n: Int) {
        dotCount = n
        dots.removeAllViews()
        dotViews = ArrayList()
        for (i in 0 until n) {
            val d = View(this)
            val lp = LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10))
            if (i > 0) lp.leftMargin = Ui.dp(this, 8)
            dots.addView(d, lp)
            dotViews.add(d)
        }
    }

    private fun updateDots(done: Int) {
        for ((i, d) in dotViews.withIndex()) {
            val on = i < done
            if (d.tag != on) {
                d.tag = on
                d.background = circle(if (on) Trainings.accent(Engine.config.key) else Ui.LINE)
            }
        }
    }

    private fun updateMeter(pct: Int, text: String) {
        setText(meterVal, text)
        meterFill.scaleX = pct / 100f
        val red = pct > 35
        if (red != meterRed) {
            meterRed = red
            meterFill.background = Ui.rounded(this, if (red) Ui.RED else Trainings.accent(Engine.config.key), 6)
        }
    }

    private fun circle(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    private fun showDone() {
        showingDone = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            camProvider?.unbindAll()
        } catch (e: Exception) {
        }
        preview?.visibility = View.GONE
        scrimView?.visibility = View.GONE

        val cfg = Engine.config
        doneTitle.text = Trainings.doneTitle(cfg.key)
        doneQuote.text = "\"" + Trainings.quotes(cfg.key).random() + "\""
        doneStats.removeAllViews()
        val rows = statRows(cfg, Engine.resultSec)
        for ((i, r) in rows.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(Ui.text(this, r.first, 14f, Ui.MUTED, Ui.REG), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Ui.text(this, r.second, 15f, Ui.TEXT, Ui.BOLD))
            doneStats.addTo(row, if (i == 0) 0 else 12)
        }
        trainingView.visibility = View.GONE
        doneView.visibility = View.VISIBLE
    }

    private fun statRows(c: Config, sec: Int): List<Pair<String, String>> {
        val total = Pair("Total time", Fmt.duration(sec.toLong()))
        return when (c.key) {
            Trainings.MEDITATE -> listOf(Pair("Duration", "${c.medMin} min"), total)
            Trainings.BREATH -> listOf(Pair("Cycles", "${c.sets}"), total)
            else -> listOf(
                Pair("Reps", "${c.sets}"),
                Pair("Seconds per rep", "${c.repSec}s"),
                total,
                Pair("Mode", Trainings.modeLabel(c.mode))
            )
        }
    }

    // ---------- leaving ----------

    private fun confirmLeave() {
        if (showingDone || Engine.isDone()) {
            finishToHome()
            return
        }
        Dialogs.show(
            this,
            "End this training?",
            "It won't be saved. Only finished trainings are added to your progress.",
            "End training",
            "Keep going",
            onPrimary = {
                Engine.cancel()
                finishToHome()
            }
        )
    }

    private fun finishToHome() {
        Engine.reset()
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    // ---------- mirror camera ----------

    /** Front camera behind the ring, for Mirror mode. If it cannot start, the training carries on without it. */
    private fun startCamera() {
        val pv = preview ?: return
        try {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    val provider = future.get()
                    val p = Preview.Builder().build()
                    p.setSurfaceProvider(pv.surfaceProvider)
                    provider.unbindAll()
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, p)
                    camProvider = provider
                } catch (e: Exception) {
                    showCamFailed()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            showCamFailed()
        }
    }

    private fun showCamFailed() {
        if (!isFinishing) camNote.visibility = View.VISIBLE
    }
}
