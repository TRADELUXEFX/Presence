package com.presence.app

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/** Poppins everywhere. Falls back to the system font if loading ever fails. */
object Fonts {
    private fun load(ctx: Context, res: Int, fallbackBold: Boolean): Typeface {
        return try {
            ResourcesCompat.getFont(ctx, res) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            if (fallbackBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    fun regular(ctx: Context): Typeface = load(ctx, R.font.poppins_regular, false)
    fun medium(ctx: Context): Typeface = load(ctx, R.font.poppins_medium, false)
    fun bold(ctx: Context): Typeface = load(ctx, R.font.poppins_bold, true)
}
