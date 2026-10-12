package com.presence.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Talks to the same Supabase project as the Presence website. The anon key is public by design. */
object Api {
    private const val URL = "https://chdnbthhmqituvrelrjg.supabase.co"
    private const val KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNoZG5idGhobXFpdHV2cmVscmpnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI5MDc1MDUsImV4cCI6MjA4ODQ4MzUwNX0.tKp1_FbquRNiilyjajlGnUGhK8dnpZff9EsBTYS1c6o"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()

    class Result(val ok: Boolean, val code: Int, val body: String) {
        /** A sentence the user can read when a request failed. */
        fun message(): String {
            if (code == 0) return "No internet connection. Please try again."
            if (code == 429) return "Too many attempts. Please wait a few minutes and try again."
            try {
                val o = JSONObject(body)
                for (k in listOf("msg", "error_description", "message", "error")) {
                    val t = o.optString(k, "")
                    if (t.isNotBlank()) return t
                }
            } catch (e: Exception) {
            }
            if (code >= 500) return "The server had a problem. Please try again in a moment."
            return "Something went wrong (error $code). Please try again."
        }
    }

    private fun call(req: Request): Result {
        return try {
            http.newCall(req).execute().use { resp ->
                Result(resp.isSuccessful, resp.code, resp.body?.string() ?: "")
            }
        } catch (e: Exception) {
            Result(false, 0, "")
        }
    }

    private fun builder(path: String, token: String): Request.Builder {
        return Request.Builder()
            .url(URL + path)
            .header("apikey", KEY)
            .header("Authorization", "Bearer $token")
    }

    // ---------- auth ----------

    /** Stores the login from a Supabase auth response. False if the response has no session in it. */
    private fun saveSession(json: String): Boolean {
        try {
            val o = JSONObject(json)
            val uid = o.optJSONObject("user")?.optString("id", "") ?: ""
            if (uid.isEmpty()) return false
            Session.save(
                o.getString("access_token"),
                o.getString("refresh_token"),
                uid,
                System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L
            )
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun signIn(email: String, password: String): Result {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val r = call(builder("/auth/v1/token?grant_type=password", KEY).post(body.toRequestBody(JSON_TYPE)).build())
        if (r.ok && !saveSession(r.body)) return Result(false, 500, "")
        return r
    }

    /** Creates the account. The caller checks Session.loggedIn(): with email confirmation on, there is no session yet. */
    fun signUp(email: String, password: String): Result {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val r = call(builder("/auth/v1/signup", KEY).post(body.toRequestBody(JSON_TYPE)).build())
        if (r.ok) saveSession(r.body)
        return r
    }

    fun sendReset(email: String): Result {
        val body = JSONObject().put("email", email).toString()
        return call(builder("/auth/v1/recover", KEY).post(body.toRequestBody(JSON_TYPE)).build())
    }

    @Synchronized
    private fun refreshToken(): Boolean {
        val refresh = Session.refresh
        if (refresh.isEmpty()) return false
        val body = JSONObject().put("refresh_token", refresh).toString()
        val r = call(builder("/auth/v1/token?grant_type=refresh_token", KEY).post(body.toRequestBody(JSON_TYPE)).build())
        if (r.ok) return saveSession(r.body)
        // The login itself was rejected: it is no longer valid. A network error keeps the login.
        if (r.code == 400 || r.code == 401) Session.clear()
        return false
    }

    private fun doAuthed(method: String, path: String, body: String?, prefer: String?): Result {
        val b = builder(path, Session.access)
        if (prefer != null) b.header("Prefer", prefer)
        val payload = (body ?: "{}").toRequestBody(JSON_TYPE)
        when (method) {
            "POST" -> b.post(payload)
            "PATCH" -> b.patch(payload)
            else -> b.get()
        }
        return call(b.build())
    }

    private fun authed(method: String, path: String, body: String?, prefer: String?): Result {
        if (Session.access.isEmpty() || System.currentTimeMillis() > Session.expiresAt - 60_000L) refreshToken()
        var r = doAuthed(method, path, body, prefer)
        if (r.code == 401 && refreshToken()) r = doAuthed(method, path, body, prefer)
        return r
    }

    // ---------- data (same tables and columns as the website) ----------

    /** The user's profile row, an empty object if they have none yet, or null if the request failed. */
    fun fetchProfile(uid: String): JSONObject? {
        val r = authed("GET", "/rest/v1/profiles?select=*&id=eq.$uid", null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            if (arr.length() == 0) JSONObject() else arr.getJSONObject(0)
        } catch (e: Exception) {
            null
        }
    }

    /** Saves the profile row. Sends updated_at the same way the website does (UTC, ISO format). */
    fun upsertProfile(row: JSONObject): Boolean {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        row.put("updated_at", fmt.format(java.util.Date()))
        return authed("POST", "/rest/v1/profiles?on_conflict=id", row.toString(), "resolution=merge-duplicates,return=minimal").ok
    }

    fun insertSession(row: JSONObject): Boolean {
        return authed("POST", "/rest/v1/sessions", row.toString(), "return=minimal").ok
    }
}
