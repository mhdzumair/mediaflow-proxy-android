package com.mediaflow.proxy.tv

import android.content.Context
import androidx.preference.PreferenceDataStore
import com.mediaflow.proxy.ConfigRepository
import com.mediaflow.proxy.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Synchronous [PreferenceDataStore] backed by the async [ConfigRepository].
 *
 * Leanback / AndroidX Preference calls `getX`/`putX` from the UI thread and
 * expects the value immediately, but DataStore is Flow-based.  We load the
 * current snapshot up-front (blocking on first read — fine during fragment
 * construction), keep it mutable in memory, and write-through to the repo on
 * a coroutine for every `putX`.  The summary refresh still sees the new
 * value immediately because we updated the snapshot before launching the save.
 */
class TvConfigDataStore(context: Context) : PreferenceDataStore() {

    private val repo = ConfigRepository(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // runBlocking is acceptable here because the initial load is required
    // before the PreferenceFragment renders — happens once, on construction.
    @Volatile
    private var snapshot: ProxyConfig = runBlocking { repo.config.first() }

    // --- String ------------------------------------------------------------

    override fun getString(key: String, defValue: String?): String? = when (key) {
        "host"                   -> snapshot.host
        "port"                   -> snapshot.port.toString()
        "apiPassword"            -> snapshot.apiPassword
        "connectTimeout"         -> snapshot.connectTimeout.toString()
        "bufferSizeKb"           -> snapshot.bufferSizeKb.toString()
        "proxyUrl"               -> snapshot.proxyUrl
        "transportRoutes"        -> snapshot.transportRoutes
        "telegramApiId"          -> snapshot.telegramApiId.toString()
        "telegramApiHash"        -> snapshot.telegramApiHash
        "telegramSessionString"  -> snapshot.telegramSessionString
        "telegramMaxConnections" -> snapshot.telegramMaxConnections.toString()
        "hlsPrebufferSegments"   -> snapshot.hlsPrebufferSegments.toString()
        "hlsSegmentCacheTtl"     -> snapshot.hlsSegmentCacheTtl.toString()
        "mpdLivePlaylistDepth"   -> snapshot.mpdLivePlaylistDepth.toString()
        "acestreamHost"          -> snapshot.acestreamHost
        "acestreamPort"          -> snapshot.acestreamPort.toString()
        "acestreamAccessToken"   -> snapshot.acestreamAccessToken
        "logLevel"               -> snapshot.logLevel
        else -> defValue
    }

    override fun putString(key: String, value: String?) {
        val v = value.orEmpty()
        val next = when (key) {
            "host"                   -> snapshot.copy(host = v)
            "port"                   -> snapshot.copy(port = v.toIntOrNull() ?: snapshot.port)
            "apiPassword"            -> snapshot.copy(apiPassword = v)
            "connectTimeout"         -> snapshot.copy(connectTimeout = v.toIntOrNull() ?: snapshot.connectTimeout)
            "bufferSizeKb"           -> snapshot.copy(bufferSizeKb = v.toIntOrNull() ?: snapshot.bufferSizeKb)
            "proxyUrl"               -> snapshot.copy(proxyUrl = v)
            "transportRoutes"        -> snapshot.copy(transportRoutes = v)
            "telegramApiId"          -> snapshot.copy(telegramApiId = v.toIntOrNull() ?: snapshot.telegramApiId)
            "telegramApiHash"        -> snapshot.copy(telegramApiHash = v)
            "telegramSessionString"  -> snapshot.copy(telegramSessionString = v)
            "telegramMaxConnections" -> snapshot.copy(telegramMaxConnections = v.toIntOrNull() ?: snapshot.telegramMaxConnections)
            "hlsPrebufferSegments"   -> snapshot.copy(hlsPrebufferSegments = v.toIntOrNull() ?: snapshot.hlsPrebufferSegments)
            "hlsSegmentCacheTtl"     -> snapshot.copy(hlsSegmentCacheTtl = v.toIntOrNull() ?: snapshot.hlsSegmentCacheTtl)
            "mpdLivePlaylistDepth"   -> snapshot.copy(mpdLivePlaylistDepth = v.toIntOrNull() ?: snapshot.mpdLivePlaylistDepth)
            "acestreamHost"          -> snapshot.copy(acestreamHost = v)
            "acestreamPort"          -> snapshot.copy(acestreamPort = v.toIntOrNull() ?: snapshot.acestreamPort)
            "acestreamAccessToken"   -> snapshot.copy(acestreamAccessToken = v)
            "logLevel"               -> snapshot.copy(logLevel = v)
            else -> return
        }
        snapshot = next
        scope.launch { repo.save(next) }
    }

    // --- Boolean -----------------------------------------------------------

    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
        "autoStart"        -> snapshot.autoStart
        "followRedirects"  -> snapshot.followRedirects
        "allProxy"         -> snapshot.allProxy
        "mpdRemuxToTs"     -> snapshot.mpdRemuxToTs
        else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        val next = when (key) {
            "autoStart"       -> snapshot.copy(autoStart = value)
            "followRedirects" -> snapshot.copy(followRedirects = value)
            "allProxy"        -> snapshot.copy(allProxy = value)
            "mpdRemuxToTs"    -> snapshot.copy(mpdRemuxToTs = value)
            else -> return
        }
        snapshot = next
        scope.launch { repo.save(next) }
    }
}
