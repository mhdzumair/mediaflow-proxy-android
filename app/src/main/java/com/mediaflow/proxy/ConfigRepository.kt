package com.mediaflow.proxy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "proxy_config")

data class ProxyConfig(
    // Basic
    val port: Int = 8888,
    val host: String = "0.0.0.0",
    val apiPassword: String = "mediaflow",
    val autoStart: Boolean = false,
    // Network
    val connectTimeout: Int = 30,
    val bufferSizeKb: Int = 256,
    val followRedirects: Boolean = true,
    val allProxy: Boolean = false,
    val proxyUrl: String = "",
    val transportRoutes: String = "",   // JSON blob
    // Telegram
    val telegramApiId: Int = 0,
    val telegramApiHash: String = "",
    val telegramSessionString: String = "",
    val telegramMaxConnections: Int = 8,
    // HLS
    val hlsPrebufferSegments: Int = 5,
    val hlsSegmentCacheTtl: Int = 300,
    // DASH/MPD
    val mpdLivePlaylistDepth: Int = 8,
    val mpdRemuxToTs: Boolean = false,
    // Acestream
    val acestreamPort: Int = 6878,
    val acestreamHost: String = "127.0.0.1",
    val acestreamAccessToken: String = "",
    // Logging
    val logLevel: String = "info",
)

class ConfigRepository(private val context: Context) {

    private object Keys {
        val PORT                    = intPreferencesKey("port")
        val HOST                    = stringPreferencesKey("host")
        val API_PASSWORD            = stringPreferencesKey("api_password")
        val AUTO_START              = booleanPreferencesKey("auto_start")
        val CONNECT_TIMEOUT         = intPreferencesKey("connect_timeout")
        val BUFFER_SIZE_KB          = intPreferencesKey("buffer_size_kb")
        val FOLLOW_REDIRECTS        = booleanPreferencesKey("follow_redirects")
        val ALL_PROXY               = booleanPreferencesKey("all_proxy")
        val PROXY_URL               = stringPreferencesKey("proxy_url")
        val TRANSPORT_ROUTES        = stringPreferencesKey("transport_routes")
        val TELEGRAM_API_ID         = intPreferencesKey("telegram_api_id")
        val TELEGRAM_API_HASH       = stringPreferencesKey("telegram_api_hash")
        val TELEGRAM_SESSION_STRING = stringPreferencesKey("telegram_session_string")
        val TELEGRAM_MAX_CONNECTIONS= intPreferencesKey("telegram_max_connections")
        val HLS_PREBUFFER_SEGMENTS  = intPreferencesKey("hls_prebuffer_segments")
        val HLS_SEGMENT_CACHE_TTL   = intPreferencesKey("hls_segment_cache_ttl")
        val MPD_LIVE_PLAYLIST_DEPTH = intPreferencesKey("mpd_live_playlist_depth")
        val MPD_REMUX_TO_TS         = booleanPreferencesKey("mpd_remux_to_ts")
        val ACESTREAM_PORT          = intPreferencesKey("acestream_port")
        val ACESTREAM_HOST          = stringPreferencesKey("acestream_host")
        val ACESTREAM_ACCESS_TOKEN  = stringPreferencesKey("acestream_access_token")
        val LOG_LEVEL               = stringPreferencesKey("log_level")
    }

    val config: Flow<ProxyConfig> = context.dataStore.data.map { p ->
        ProxyConfig(
            port                  = p[Keys.PORT]                    ?: 8888,
            host                  = p[Keys.HOST]                    ?: "0.0.0.0",
            apiPassword           = p[Keys.API_PASSWORD]            ?: "mediaflow",
            autoStart             = p[Keys.AUTO_START]              ?: false,
            connectTimeout        = p[Keys.CONNECT_TIMEOUT]         ?: 30,
            bufferSizeKb          = p[Keys.BUFFER_SIZE_KB]          ?: 256,
            followRedirects       = p[Keys.FOLLOW_REDIRECTS]        ?: true,
            allProxy              = p[Keys.ALL_PROXY]               ?: false,
            proxyUrl              = p[Keys.PROXY_URL]               ?: "",
            transportRoutes       = p[Keys.TRANSPORT_ROUTES]        ?: "",
            telegramApiId         = p[Keys.TELEGRAM_API_ID]         ?: 0,
            telegramApiHash       = p[Keys.TELEGRAM_API_HASH]       ?: "",
            telegramSessionString = p[Keys.TELEGRAM_SESSION_STRING] ?: "",
            telegramMaxConnections= p[Keys.TELEGRAM_MAX_CONNECTIONS]?: 8,
            hlsPrebufferSegments  = p[Keys.HLS_PREBUFFER_SEGMENTS]  ?: 5,
            hlsSegmentCacheTtl    = p[Keys.HLS_SEGMENT_CACHE_TTL]   ?: 300,
            mpdLivePlaylistDepth  = p[Keys.MPD_LIVE_PLAYLIST_DEPTH] ?: 8,
            mpdRemuxToTs          = p[Keys.MPD_REMUX_TO_TS]         ?: false,
            acestreamPort         = p[Keys.ACESTREAM_PORT]           ?: 6878,
            acestreamHost         = p[Keys.ACESTREAM_HOST]          ?: "127.0.0.1",
            acestreamAccessToken  = p[Keys.ACESTREAM_ACCESS_TOKEN]  ?: "",
            logLevel              = p[Keys.LOG_LEVEL]               ?: "info",
        )
    }

    suspend fun save(cfg: ProxyConfig) {
        context.dataStore.edit { p ->
            p[Keys.PORT]                    = cfg.port
            p[Keys.HOST]                    = cfg.host
            p[Keys.API_PASSWORD]            = cfg.apiPassword
            p[Keys.AUTO_START]              = cfg.autoStart
            p[Keys.CONNECT_TIMEOUT]         = cfg.connectTimeout
            p[Keys.BUFFER_SIZE_KB]          = cfg.bufferSizeKb
            p[Keys.FOLLOW_REDIRECTS]        = cfg.followRedirects
            p[Keys.ALL_PROXY]               = cfg.allProxy
            p[Keys.PROXY_URL]               = cfg.proxyUrl
            p[Keys.TRANSPORT_ROUTES]        = cfg.transportRoutes
            p[Keys.TELEGRAM_API_ID]         = cfg.telegramApiId
            p[Keys.TELEGRAM_API_HASH]       = cfg.telegramApiHash
            p[Keys.TELEGRAM_SESSION_STRING] = cfg.telegramSessionString
            p[Keys.TELEGRAM_MAX_CONNECTIONS]= cfg.telegramMaxConnections
            p[Keys.HLS_PREBUFFER_SEGMENTS]  = cfg.hlsPrebufferSegments
            p[Keys.HLS_SEGMENT_CACHE_TTL]   = cfg.hlsSegmentCacheTtl
            p[Keys.MPD_LIVE_PLAYLIST_DEPTH] = cfg.mpdLivePlaylistDepth
            p[Keys.MPD_REMUX_TO_TS]         = cfg.mpdRemuxToTs
            p[Keys.ACESTREAM_PORT]          = cfg.acestreamPort
            p[Keys.ACESTREAM_HOST]          = cfg.acestreamHost
            p[Keys.ACESTREAM_ACCESS_TOKEN]  = cfg.acestreamAccessToken
            p[Keys.LOG_LEVEL]               = cfg.logLevel
        }
    }
}
