package com.presense.app

import android.content.Context
import android.widget.ImageView

/** The official multicolor Google "G" (vector). */
class GoogleGView(c: Context) : ImageView(c) {
    init {
        setImageResource(R.drawable.ic_google_g)
        scaleType = ScaleType.FIT_CENTER
    }
}
