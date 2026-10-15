package com.presence.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Plays one saved training recording (the screen with its sound) inside the app. */
class PlayerActivity : BaseActivity() {
    private var video: VideoView? = null
    private var chromeOn = true   // back button, control bar and system bars showing
    private val handler = Handler(Looper.getMainLooper())

    private fun fmt(ms: Int): String {
        val s = Math.max(0, ms) / 1000
        return String.format("%02d:%02d", s / 60, s % 60)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // window geometry must not change when the bars hide, or the video would shift/resize
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        } else if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        // dark player: light icons on the bottom navigation bar, which sits on black
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        val c = this
        val uri = intent.getStringExtra("uri") ?: run { finish(); return }
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
        val glass = Color.parseColor("#99000000")
        val edge = Color.parseColor("#59FFFFFF")

        // the video fills the whole screen; everything else floats on top of it, so nothing can resize it
        val page = FrameLayout(c)
        page.setBackgroundColor(Color.BLACK)
        val vv = VideoView(c)
        video = vv
        page.addView(vv, FrameLayout.LayoutParams(match, match, Gravity.CENTER))

        // back: just a round glass button, so it covers as little of the video as possible
        val back = FrameLayout(c)
        back.background = Ui.rounded(c, glass, 22, edge, 1)
        back.contentDescription = "Back"
        back.setOnClickListener { finish() }
        back.addView(FsIconView(c, "M15 18l-6-6 6-6"), FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER))
        val blp = FrameLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44), Gravity.TOP or Gravity.START)
        blp.leftMargin = Ui.dp(c, 12)
        page.addView(back, blp)

        // one slim glass bar at the bottom: play/pause, time, seek line, length
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = Ui.rounded(c, glass, 28, edge, 1)
        bar.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6))

        val playPause = FrameLayout(c)
        playPause.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 20)
        val playIcon = FsIconView(c, Icons.PLAY)
        val pauseIcon = FsIconView(c, "M8 5v14 M16 5v14")
        playPause.addView(playIcon, FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        playPause.addView(pauseIcon, FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        bar.addView(playPause, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)))

        val cur: TextView = Ui.text(c, "00:00", 13f, Color.WHITE, Ui.MED)
        // fixed-width time boxes: the digits change width as they count, which would otherwise stretch and shrink the seek line
        cur.gravity = Gravity.CENTER
        cur.maxLines = 1
        val clp = LinearLayout.LayoutParams(Ui.dp(c, 46), LinearLayout.LayoutParams.WRAP_CONTENT)
        clp.leftMargin = Ui.dp(c, 8)
        bar.addView(cur, clp)

        val seek = SeekBar(c)
        seek.progressTintList = ColorStateList.valueOf(Color.parseColor("#1E90FF"))
        seek.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#59FFFFFF"))
        seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        bar.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val total: TextView = Ui.text(c, "00:00", 13f, Color.WHITE, Ui.MED)
        total.gravity = Gravity.CENTER
        total.maxLines = 1
        bar.addView(total, LinearLayout.LayoutParams(Ui.dp(c, 46), LinearLayout.LayoutParams.WRAP_CONTENT))

        val barLp = FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM)
        barLp.leftMargin = Ui.dp(c, 12)
        barLp.rightMargin = Ui.dp(c, 12)
        page.addView(bar, barLp)

        // bars' full size whether or not they are showing, so nothing moves when they hide
        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            blp.topMargin = bars.top + Ui.dp(c, 8)
            back.layoutParams = blp
            barLp.bottomMargin = bars.bottom + Ui.dp(c, 12)
            bar.layoutParams = barLp
            insets
        }
        ViewCompat.requestApplyInsets(page)
        setContentView(page)

        // ---- playback wiring
        var dragging = false
        fun syncIcons() {
            val playing = vv.isPlaying
            playIcon.visibility = if (playing) View.GONE else View.VISIBLE
            pauseIcon.visibility = if (playing) View.VISIBLE else View.GONE
            playPause.contentDescription = if (playing) "Pause" else "Play"
        }
        val tick = object : Runnable {
            override fun run() {
                if (!dragging) {
                    val pos = vv.currentPosition
                    seek.progress = pos
                    cur.text = fmt(pos)
                }
                syncIcons()
                handler.postDelayed(this, 250)
            }
        }
        playPause.setOnClickListener {
            if (vv.isPlaying) vv.pause() else {
                if (vv.duration > 0 && vv.currentPosition >= vv.duration - 200) vv.seekTo(0)
                vv.start()
            }
            syncIcons()
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { cur.text = fmt(p); vv.seekTo(p) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) { dragging = false }
        })
        vv.setOnPreparedListener {
            it.isLooping = false
            seek.max = vv.duration
            total.text = fmt(vv.duration)
            vv.start()
            syncIcons()
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        vv.setOnCompletionListener { seek.progress = seek.max; cur.text = fmt(vv.duration); syncIcons() }
        vv.setOnErrorListener { _, _, _ ->
            Toast.makeText(c, "Couldn't play this recording", Toast.LENGTH_SHORT).show()
            finish()
            true
        }

        // like a gallery: tap the video to hide everything (back button, controls, system bars) and tap again to bring it back; the video itself never moves
        vv.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                chromeOn = !chromeOn
                val ic = WindowCompat.getInsetsController(window, window.decorView)
                val vis = if (chromeOn) View.VISIBLE else View.INVISIBLE
                back.visibility = vis
                bar.visibility = vis
                if (chromeOn) {
                    ic.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    ic.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            true
        }
        try {
            vv.setVideoURI(Uri.parse(uri))
        } catch (e: Exception) {
            Toast.makeText(c, "Couldn't play this recording", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        try { video?.pause() } catch (e: Exception) { android.util.Log.w("Presence", "PlayerActivity step failed", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { video?.stopPlayback() } catch (e: Exception) { android.util.Log.w("Presence", "PlayerActivity step failed", e) }
    }
}
