package com.mediaflow.proxy

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/mhdzumair/mediaflow-proxy-android/releases/latest"
    private const val PREF_NAME = "update_checker"
    private const val PREF_LAST_CHECK = "last_check_ms"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    data class UpdateInfo(val version: String, val downloadUrl: String)

    /**
     * Returns [UpdateInfo] when a newer release exists, null otherwise.
     *
     * Silent (background) calls are rate-limited to once per 24 h.
     * Pass [force] = true to bypass the cooldown (manual "Check for updates").
     */
    suspend fun check(ctx: Context, force: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                if (!force) {
                    val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    val last = prefs.getLong(PREF_LAST_CHECK, 0L)
                    if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return@withContext null
                    prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
                }

                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = JSONObject(body)
                val latestTag = root.getString("tag_name").trimStart('v')
                // VERSION_NAME is "1.0.7" on mobile, "1.0.7-tv" on tv flavor
                val currentVersion = BuildConfig.VERSION_NAME.removeSuffix("-tv")

                if (!isNewer(latestTag, currentVersion)) return@withContext null

                val flavor = BuildConfig.FLAVOR           // "mobile" or "tv"
                val abi = resolveAbi()
                val preferred = "mediaflow-proxy-$flavor-$abi.apk"
                val fallback  = "mediaflow-proxy-$flavor-universal.apk"

                val assets = root.getJSONArray("assets")
                var preferredUrl: String? = null
                var fallbackUrl:  String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name  = asset.getString("name")
                    val url   = asset.getString("browser_download_url")
                    if (name == preferred) preferredUrl = url
                    if (name == fallback)  fallbackUrl  = url
                }

                val downloadUrl = preferredUrl ?: fallbackUrl ?: return@withContext null
                UpdateInfo(latestTag, downloadUrl)
            } catch (_: Exception) {
                null
            }
        }

    private fun resolveAbi(): String {
        val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: return "universal"
        return when {
            primary.startsWith("arm64")   -> "arm64-v8a"
            primary.startsWith("armeabi") -> "armeabi-v7a"
            primary.startsWith("x86_64")  -> "x86_64"
            primary.startsWith("x86")     -> "x86"
            else                          -> "universal"
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val c = parseVersion(candidate)
        val v = parseVersion(current)
        for (i in 0 until maxOf(c.size, v.size)) {
            val a = c.getOrElse(i) { 0 }
            val b = v.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.split(".").mapNotNull { it.toIntOrNull() }
}
