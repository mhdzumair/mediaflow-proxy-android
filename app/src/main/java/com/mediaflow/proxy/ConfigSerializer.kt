package com.mediaflow.proxy

import org.json.JSONObject

/**
 * JSON (de)serialisation for [ProxyConfig] so the same blob can be exported /
 * imported on phone and TV.  Uses `org.json` (built into Android) to avoid an
 * extra dependency.
 *
 * Format: a single `{ "version": 1, "fields": { ... } }` object where `fields`
 * mirrors the [ProxyConfig] property names 1-to-1.  Missing fields on import
 * fall back to the current config's value so the format is forward-compatible.
 */
object ConfigSerializer {

    private const val VERSION = 1

    fun toJson(cfg: ProxyConfig, pretty: Boolean = true): String {
        val fields = JSONObject().apply {
            put("port", cfg.port)
            put("host", cfg.host)
            put("apiPassword", cfg.apiPassword)
            put("autoStart", cfg.autoStart)
            put("connectTimeout", cfg.connectTimeout)
            put("bufferSizeKb", cfg.bufferSizeKb)
            put("followRedirects", cfg.followRedirects)
            put("allProxy", cfg.allProxy)
            put("proxyUrl", cfg.proxyUrl)
            put("transportRoutes", cfg.transportRoutes)
            put("telegramApiId", cfg.telegramApiId)
            put("telegramApiHash", cfg.telegramApiHash)
            put("telegramSessionString", cfg.telegramSessionString)
            put("telegramMaxConnections", cfg.telegramMaxConnections)
            put("hlsPrebufferSegments", cfg.hlsPrebufferSegments)
            put("hlsSegmentCacheTtl", cfg.hlsSegmentCacheTtl)
            put("mpdLivePlaylistDepth", cfg.mpdLivePlaylistDepth)
            put("mpdRemuxToTs", cfg.mpdRemuxToTs)
            put("acestreamPort", cfg.acestreamPort)
            put("acestreamHost", cfg.acestreamHost)
            put("acestreamAccessToken", cfg.acestreamAccessToken)
            put("logLevel", cfg.logLevel)
        }
        val root = JSONObject().put("version", VERSION).put("fields", fields)
        return if (pretty) root.toString(2) else root.toString()
    }

    /** Parse a config blob.  Unknown / missing keys fall back to [fallback]'s value,
     *  so bumping VERSION with added fields doesn't break older exports. */
    fun fromJson(json: String, fallback: ProxyConfig = ProxyConfig()): ProxyConfig {
        val root = JSONObject(json)
        // Accept both {version, fields:{…}} and a flat {…} object (very old exports).
        val fields = if (root.has("fields")) root.getJSONObject("fields") else root
        return ProxyConfig(
            port                  = fields.optInt("port", fallback.port),
            host                  = fields.optString("host", fallback.host),
            apiPassword           = fields.optString("apiPassword", fallback.apiPassword),
            autoStart             = fields.optBoolean("autoStart", fallback.autoStart),
            connectTimeout        = fields.optInt("connectTimeout", fallback.connectTimeout),
            bufferSizeKb          = fields.optInt("bufferSizeKb", fallback.bufferSizeKb),
            followRedirects       = fields.optBoolean("followRedirects", fallback.followRedirects),
            allProxy              = fields.optBoolean("allProxy", fallback.allProxy),
            proxyUrl              = fields.optString("proxyUrl", fallback.proxyUrl),
            transportRoutes       = fields.optString("transportRoutes", fallback.transportRoutes),
            telegramApiId         = fields.optInt("telegramApiId", fallback.telegramApiId),
            telegramApiHash       = fields.optString("telegramApiHash", fallback.telegramApiHash),
            telegramSessionString = fields.optString("telegramSessionString", fallback.telegramSessionString),
            telegramMaxConnections= fields.optInt("telegramMaxConnections", fallback.telegramMaxConnections),
            hlsPrebufferSegments  = fields.optInt("hlsPrebufferSegments", fallback.hlsPrebufferSegments),
            hlsSegmentCacheTtl    = fields.optInt("hlsSegmentCacheTtl", fallback.hlsSegmentCacheTtl),
            mpdLivePlaylistDepth  = fields.optInt("mpdLivePlaylistDepth", fallback.mpdLivePlaylistDepth),
            mpdRemuxToTs          = fields.optBoolean("mpdRemuxToTs", fallback.mpdRemuxToTs),
            acestreamPort         = fields.optInt("acestreamPort", fallback.acestreamPort),
            acestreamHost         = fields.optString("acestreamHost", fallback.acestreamHost),
            acestreamAccessToken  = fields.optString("acestreamAccessToken", fallback.acestreamAccessToken),
            logLevel              = fields.optString("logLevel", fallback.logLevel),
        )
    }
}
