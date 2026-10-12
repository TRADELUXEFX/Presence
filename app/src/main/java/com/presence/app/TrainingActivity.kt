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
    private lateinit var trainingView: View
    private lateinit var topStack: LinearLayout
    private lateinit var doneView: ScrollView
    private lateinit var progress: TextView
    private lateinit var popDots: PopDotsView
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
    private var lensFront = true
    private val mirrorSegs = ArrayList<View>()
    private lateinit var mirrorRep: TextView
    private lateinit var mirrorSentence: TextView
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
        if (!cameraOn) Ui.setNavColor(c, Ui.mix(Trainings.accent(Engine.config.key), Color.BLACK, 0.2f))
        val fg = if (cameraOn) Color.WHITE else Ui.TEXT
        val muted = if (cameraOn) Color.parseColor("#D9FFFFFF") else Ui.MUTED

        val frame = FrameLayout(c)
        frame.setBackgroundColor(if (cameraOn) Color.BLACK else Ui.BG)
        if (cameraOn) {
            val pv = PreviewView(c)
            // compatible mode draws the picture in the normal view tree, so the ring and text sit on top of it (as on PauzFirst)
            pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            pv.scaleType = PreviewView.ScaleType.FILL_CENTER
            preview = pv
            frame.addView(pv, FrameLayout.LayoutParams(-1, -1))
        }

        val content = FrameLayout(c)
        trainingView = buildTraining(c, fg, muted)
        doneView = buildDone(c, fg, muted)
        content.addView(trainingView, FrameLayout.LayoutParams(-1, -1))
        content.addView(doneView, FrameLayout.LayoutParams(-1, -1))
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        if (!cameraOn) {
            val header = Ui.header(c, Trainings.name(Engine.config.key), Trainings.accent(Engine.config.key), infoAction()) { confirmLeave() }
            frame.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            // the count and dashes start just under the header
            header.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
                if (::topStack.isInitialized) topStack.setPadding(Ui.dp(c, 24), bottom + Ui.dp(c, 16), Ui.dp(c, 24), 0)
                if (::doneView.isInitialized) doneView.setPadding(0, bottom + Ui.dp(c, 16), 0, 0)
            }
        }
        if (cameraOn) frame.addView(buildMirrorTop(c), FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(frame)
    }

    /** Mirror mode top: back button, the training name, a thin progress bar, and the instruction card. Translucent, over the camera. */
    private fun buildMirrorTop(c: Context): LinearLayout {
        val key = Engine.config.key
        val topBox = LinearLayout(c)
        topBox.orientation = LinearLayout.VERTICAL
        topBox.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        Ui.padInsets(topBox, top = true)

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val size = Ui.dp(c, 40)
        val back = FrameLayout(c)
        back.background = Ui.rounded(c, Color.parseColor("#66000000"), 20)
        back.contentDescription = "Back"
        back.setOnClickListener { confirmLeave() }
        back.addView(FsIconView(c, "M15 18l-6-6 6-6", Color.WHITE), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        row.addView(back, LinearLayout.LayoutParams(size, size))

        val name = Ui.text(c, Trainings.name(key).uppercase(), 13f, Color.WHITE, Ui.BOLD)
        name.letterSpacing = 0.15f
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.background = Ui.rounded(c, Color.parseColor("#59000000"), 20, Color.parseColor("#1FFFFFFF"), 1)
        name.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 10))
        // back arrow on the left, the training name pushed to the right edge
        row.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(name, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        topBox.addView(row)

        val track = LinearLayout(c)
        track.orientation = LinearLayout.HORIZONTAL
        mirrorSegs.clear()
        for (i in 0 until Math.max(1, Engine.totalReps)) {
            val seg = View(c)
            seg.background = Ui.rounded(c, Color.parseColor("#55FFFFFF"), 2)
            mirrorSegs.add(seg)
            val segLp = LinearLayout.LayoutParams(0, Ui.dp(c, 4), 1f)
            if (i > 0) segLp.leftMargin = Ui.dp(c, 4)
            track.addView(seg, segLp)
        }
        val trackLp = LinearLayout.LayoutParams(-1, Ui.dp(c, 4))
        trackLp.topMargin = Ui.dp(c, 14)
        topBox.addView(track, trackLp)

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.background = Ui.rounded(c, Color.parseColor("#73000000"), 16, Color.parseColor("#2EFFFFFF"), 1)
        card.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 18))
        mirrorRep = Ui.text(c, "", 12f, Color.parseColor("#BFFFFFFF"), Ui.BOLD)
        mirrorRep.letterSpacing = 0.15f
        mirrorRep.gravity = Gravity.CENTER
        card.addView(mirrorRep)
        mirrorSentence = Ui.text(c, "", 20f, Color.WHITE, Ui.BOLD)
        mirrorSentence.gravity = Gravity.CENTER
        mirrorSentence.setLineSpacing(0f, 1.2f)
        card.addTo(mirrorSentence, 6)
        val cardLp = LinearLayout.LayoutParams(-1, -2)
        cardLp.topMargin = Ui.dp(c, 16)
        topBox.addView(card, cardLp)
        return topBox
    }

    /** Mirror mode: fills the bar and the card from the engine. Only touches the overlay views. */
    private fun renderMirror() {
        setText(mirrorRep, Engine.progressLabel().uppercase())
        setText(mirrorSentence, Engine.instrText)
        val accent = Trainings.accent(Engine.config.key)
        for ((i, seg) in mirrorSegs.withIndex()) {
            seg.background = Ui.rounded(this, if (i < Engine.doneReps) accent else Color.parseColor("#55FFFFFF"), 2)
        }
    }

    /** Mirror mode: only the two buttons at the bottom. No ring, count, name or text, so you can see yourself. */
    private fun buildMirrorControls(c: Context): LinearLayout {
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.BOTTOM
        v.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 24))
        // lift the buttons above the system navigation bar (it was covering them)
        Ui.padInsets(v, bottom = true)

        camNote = Ui.text(c, "Camera not available. Training continues without it.", 12f, Ui.AMBER, Ui.MED)
        camNote.gravity = Gravity.CENTER
        camNote.visibility = View.GONE
        v.addTo(camNote, 4)

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val flip = Ui.pill(c, "Flip camera", Color.parseColor("#99000000"), Color.WHITE)
        flip.setOnClickListener { flipCamera() }
        row.addView(flip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val end = Ui.pill(c, "End training", Color.parseColor("#99000000"), Color.WHITE)
        end.setOnClickListener { endNow() }
        val endLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        endLp.leftMargin = Ui.dp(c, 10)
        row.addView(end, endLp)
        v.addTo(row, 0)
        return v
    }

    private fun buildTraining(c: Context, fg: Int, muted: Int): View {
        if (cameraOn) return buildMirrorControls(c)
        val root = FrameLayout(c)
        if (Engine.config.key == Trainings.FIDGET) {
            root.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) Engine.touch()
                false
            }
        }

        // top: the count pill and the dashes
        val top = LinearLayout(c)
        top.orientation = LinearLayout.VERTICAL
        top.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), 0)
        topStack = top
        camNote = Ui.text(c, "Camera not available. Training continues without it.", 12f, Ui.AMBER, Ui.MED)
        camNote.gravity = Gravity.CENTER
        camNote.visibility = View.GONE
        top.addTo(camNote, 4)

        val pillRow = LinearLayout(c)
        pillRow.orientation = LinearLayout.HORIZONTAL
        pillRow.gravity = Gravity.CENTER_VERTICAL
        badge = Ui.chip(c, "", Trainings.ink(Engine.config.key), Trainings.soft(Engine.config.key))
        badge.visibility = View.INVISIBLE
        pillRow.addView(badge, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // the count, e.g. 1/10, sits in its own pill beside the phase badge
        progress = Ui.text(c, "", 16f, Trainings.ink(Engine.config.key), Ui.BOLD)
        progress.gravity = Gravity.CENTER
        progress.background = Ui.rounded(c, Trainings.soft(Engine.config.key), 20)
        progress.setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9))
        progress.visibility = View.GONE
        val countLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        countLp.leftMargin = Ui.dp(c, 10)
        pillRow.addView(progress, countLp)
        top.addTo(pillRow, 14)

        dots = LinearLayout(c)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER_VERTICAL
        top.addTo(dots, 12)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // middle: the ring, centred on the screen
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
        ringBox.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        popDots = PopDotsView(c, Ui.TEXT, 12)
        popDots.visibility = View.GONE
        ringBox.addView(popDots, FrameLayout.LayoutParams(-1, -1))
        root.addView(ringBox, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // bottom: the instruction and motion meter, then End training in its own curved bar (like Start training)
        val bottom = LinearLayout(c)
        bottom.orientation = LinearLayout.VERTICAL
        val text = LinearLayout(c)
        text.orientation = LinearLayout.VERTICAL
        text.setPadding(Ui.dp(c, 24), 0, Ui.dp(c, 24), Ui.dp(c, 16))
        instr = Ui.text(c, "", 16f, fg, Ui.MED)
        instr.gravity = Gravity.CENTER
        instr.setLineSpacing(0f, 1.25f)
        text.addView(instr, LinearLayout.LayoutParams(-1, -2))
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
        text.addTo(meter, 0)
        bottom.addView(text, LinearLayout.LayoutParams(-1, -2))

        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.tintedBottomBar(c, bar, Trainings.accent(Engine.config.key))
        bar.elevation = Ui.dp(c, 8).toFloat()
        val stop = Ui.pill(c, "End training", Color.WHITE, Ui.TEXT)
        stop.setOnClickListener { endNow() }
        bar.addView(stop, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(bar, bottom = true)
        bottom.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return root
    }

    private fun buildDone(c: Context, fg: Int, muted: Int): ScrollView {
        val sv = ScrollView(c)
        sv.isFillViewport = true
        sv.visibility = View.GONE
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        v.setPadding(Ui.dp(c, 22), Ui.dp(c, 28), Ui.dp(c, 22), Ui.dp(c, 28))

        doneTitle = Ui.text(c, "", 28f, fg, Ui.BOLD)
        doneTitle.gravity = Gravity.CENTER
        v.addView(doneTitle)
        doneQuote = Ui.text(c, "", 15f, muted, Ui.REG)
        doneQuote.gravity = Gravity.CENTER
        doneQuote.setLineSpacing(0f, 1.3f)
        v.addTo(doneQuote, 12)

        doneStats = Ui.card(c, Trainings.soft(Engine.config.key))
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
        if (cameraOn) {
            renderMirror()
            return
        }
        val e = Engine.config
        val eng = Engine
        ring.fraction = eng.ringFraction
        ring.ringColor = eng.ringColor
        setText(ringNum, eng.ringText)
        setText(ringUnit, eng.ringUnit)
        setText(badge, eng.badgeText)
        badge.visibility = if (eng.badgeText.isEmpty()) View.INVISIBLE else View.VISIBLE
        setText(instr, eng.instrText)
        val count = Regex("(\\d+) of (\\d+)").find(eng.progressLabel())
        setText(progress, if (count != null) "${count.groupValues[1]}/${count.groupValues[2]}" else "")
        progress.visibility = if (count != null) View.VISIBLE else View.GONE
        popDots.visibility = if (eng.ringText.isEmpty()) View.VISIBLE else View.GONE
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
            // a dash per rep, sharing the full width
            val lp = LinearLayout.LayoutParams(0, Ui.dp(this, 4), 1f)
            if (i > 0) lp.leftMargin = Ui.dp(this, 4)
            dots.addView(d, lp)
            dotViews.add(d)
        }
    }

    private fun updateDots(done: Int) {
        for ((i, d) in dotViews.withIndex()) {
            val on = i < done
            if (d.tag != on) {
                d.tag = on
                d.background = Ui.rounded(this, if (on) Trainings.accent(Engine.config.key) else Ui.LINE, 2)
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

    /** Info button on the right of the header: says what this training is about. */
    private fun infoAction(): View {
        return Ui.infoButton(this, Ui.onColor(Trainings.accent(Engine.config.key))) { showAbout() }
    }

    /** The pop-up behind the info button, with its button in the training's colour. */
    private fun showAbout() {
        if (isFinishing) return
        val key = Engine.config.key
        val tint = Trainings.accent(key)
        val d = android.app.Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = Ui.card(this)
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22))
        card.addView(Ui.text(this, Trainings.name(key), 18f, Ui.TEXT, Ui.BOLD))
        val body = Ui.text(this, Trainings.about(key), 14f, Ui.MUTED, Ui.REG)
        body.setLineSpacing(0f, 1.25f)
        card.addTo(body, 8)
        val ok = Ui.pill(this, "Got it", tint, Ui.onColor(tint))
        ok.setOnClickListener { d.dismiss() }
        card.addTo(ok, 20)
        Ui.noClipDeep(card)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** Leaving a training never asks for confirmation; the training ends straight away. */
    private fun confirmLeave() {
        endNow()
    }

    /** End training button: stops straight away, no confirmation (the back arrow still asks). */
    private fun endNow() {
        if (!showingDone && !Engine.isDone()) Engine.cancel()
        finishToHome()
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
                    val sel = if (lensFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    provider.bindToLifecycle(this, sel, p)
                    camProvider = provider
                } catch (e: Exception) {
                    if (!lensFront) {
                        lensFront = true
                        startCamera()
                    } else {
                        showCamFailed()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            showCamFailed()
        }
    }

    private fun flipCamera() {
        lensFront = !lensFront
        startCamera()
    }

    private fun showCamFailed() {
        if (!isFinishing) camNote.visibility = View.VISIBLE
    }
}
