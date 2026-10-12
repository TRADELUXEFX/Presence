package com.presence.app

import android.content.Context
import android.content.SharedPreferences

/** Login tokens, kept in private SharedPreferences. */
object Session {
    lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("presence", Context.MODE_PRIVATE)
    }

    val access: String get() = prefs.getString("access", "") ?: ""
    val refresh: String get() = prefs.getString("refresh", "") ?: ""
    val userId: String get() = prefs.getString("uid", "") ?: ""
    val expiresAt: Long get() = prefs.getLong("expires_at", 0L)

    fun loggedIn(): Boolean = refresh.isNotEmpty() && userId.isNotEmpty()

    fun save(access: String, refresh: String, uid: String, expiresAt: Long) {
        prefs.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .putString("uid", uid)
            .putLong("expires_at", expiresAt)
            .apply()
    }

    fun clear() {
        prefs.edit().remove("access").remove("refresh").remove("uid").remove("expires_at").apply()
    }
}
