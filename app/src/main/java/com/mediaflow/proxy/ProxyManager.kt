package com.mediaflow.proxy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ProxyManager"

class ProxyManager(private val context: Context) {

    private var process: Process? = null
    private val startMutex = Mutex()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 500)
    val logs: SharedFlow<String> = _logs

    private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRunningFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning

    val isRunning: Boolean get() = process?.isAlive == true

    private val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/libmediaflow-proxy.so"

    private val pidFile: File
        get() = File(context.filesDir, "proxy.pid")

    suspend fun start(config: ProxyConfig): Boolean = startMutex.withLock {
        withContext(Dispatchers.IO) {
            if (isRunning) return@withContext true

            val binary = File(binaryPath)
            if (!binary.exists()) {
                _logs.tryEmit("ERROR: Proxy binary not found at $binaryPath")
                return@withContext false
            }
            if (!binary.canExecute()) {
                _logs.tryEmit("ERROR: Proxy binary not executable — reinstall the app")
                return@withContext false
            }

            // Kill any orphaned proxy subprocess from a previous crash
            killOrphan()

            _logs.tryEmit("Starting proxy on port ${config.port}…")

            val env = buildMap<String, String> {
                put("APP__SERVER__HOST", config.host.ifEmpty { "0.0.0.0" })
                put("APP__SERVER__PORT", config.port.toString())
                put("APP__AUTH__API_PASSWORD", config.apiPassword.ifEmpty { "mediaflow" })
                put("APP__PROXY__CONNECT_TIMEOUT", config.connectTimeout.toString())
                put("APP__PROXY__BUFFER_SIZE", (config.bufferSizeKb * 1024).toString())
                put("APP__PROXY__FOLLOW_REDIRECTS", config.followRedirects.toString())
                put("APP__PROXY__ALL_PROXY", config.allProxy.toString())
                if (config.proxyUrl.isNotEmpty()) put("APP__PROXY__PROXY_URL", config.proxyUrl)
                if (config.transportRoutes.isNotEmpty()) put("APP__PROXY__TRANSPORT_ROUTES", config.transportRoutes)
                if (config.telegramApiId > 0) put("APP__TELEGRAM__API_ID", config.telegramApiId.toString())
                if (config.telegramApiHash.isNotEmpty()) put("APP__TELEGRAM__API_HASH", config.telegramApiHash)
                if (config.telegramSessionString.isNotEmpty()) put("APP__TELEGRAM__SESSION_STRING", config.telegramSessionString)
                put("APP__TELEGRAM__MAX_CONNECTIONS", config.telegramMaxConnections.toString())
                put("APP__HLS__PREBUFFER_SEGMENTS", config.hlsPrebufferSegments.toString())
                put("APP__HLS__SEGMENT_CACHE_TTL", config.hlsSegmentCacheTtl.toString())
                put("APP__MPD__LIVE_PLAYLIST_DEPTH", config.mpdLivePlaylistDepth.toString())
                put("APP__MPD__REMUX_TO_TS", config.mpdRemuxToTs.toString())
                put("APP__ACESTREAM__PORT", config.acestreamPort.toString())
                put("APP__ACESTREAM__HOST", config.acestreamHost.ifEmpty { "127.0.0.1" })
                if (config.acestreamAccessToken.isNotEmpty()) put("APP__ACESTREAM__ACCESS_TOKEN", config.acestreamAccessToken)
                // Log level: configurable via APP__LOG_LEVEL (falls back to RUST_LOG in the binary)
                put("APP__LOG_LEVEL", config.logLevel)
                put("NO_COLOR", "1")
                put("TERM", "dumb")
                // Point ffmpeg to the bundled binary (if present)
                val ffmpegBin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg-proxy.so")
                if (ffmpegBin.exists()) put("FFMPEG_PATH", ffmpegBin.absolutePath)
            }

            return@withContext try {
                val proc = ProcessBuilder(binary.absolutePath)
                    .directory(context.filesDir)
                    .redirectErrorStream(true)
                    .apply { environment().putAll(env) }
                    .start()

                process = proc
                _isRunning.value = true
                savePid(proc)

                Thread {
                    try {
                        proc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val clean = cleanLogLine(line)
                                if (clean.isNotEmpty()) {
                                    Log.d(TAG, clean)
                                    _logs.tryEmit(clean)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Process was destroyed — expected on stop
                    } finally {
                        // Stream ended: process exited (cleanly or crashed). Update state so
                        // the UI reflects reality instead of showing "Running" indefinitely.
                        if (process === proc) {
                            process = null
                            _isRunning.value = false
                            _logs.tryEmit("Proxy process exited.")
                        }
                    }
                }.also { it.isDaemon = true }.start()

                true
            } catch (e: Exception) {
                _logs.tryEmit("ERROR: Failed to start proxy: ${e.message}")
                false
            }
        }
    }

    fun stop() {
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
        _isRunning.value = false
        pidFile.delete()
    }

    // -------------------------------------------------------------------------

    /**
     * Clean a raw log line from the Rust binary:
     *  - Strip ANSI escape codes (color, cursor) — belt-and-suspenders alongside NO_COLOR=1
     *  - Strip the cargo registry source path embedded at compile time
     *    e.g. "… actix_server: /Users/user/.cargo/registry/.../file.rs:310: message"
     *       → "… actix_server: message"
     *  - Keep our own crate name short
     */
    private fun cleanLogLine(line: String): String {
        var s = line
            .replace(Regex("\u001B\\[[0-9;]*[mK]"), "")   // ANSI SGR / erase
            .replace(Regex("\u001B\\[\\?[0-9;]*[hl]"), "") // ANSI DEC private
            .trimEnd()

        // Strip embedded source-file paths — matches both absolute (/path/file.rs:N:)
        // and relative (src/file.rs:N:) forms that Rust tracing emits.
        s = s.replace(Regex("""[^\s]+\.rs:\d+:?\s*"""), "")

        // Strip ThreadId noise: " main ThreadId(01) " / "actix-rt|system:0|arbiter:2 ThreadId(04) "
        s = s.replace(Regex("""[\w|:]+\s+ThreadId\(\d+\)\s"""), "")

        return s.trim()
    }

    private fun savePid(proc: Process) {
        try {
            // Get PID via reflection (works on all API levels for Android's ProcessImpl)
            val pid: Long = try {
                proc.javaClass.getDeclaredField("pid")
                    .also { it.isAccessible = true }
                    .getInt(proc).toLong()
            } catch (_: Exception) { -1L }
            pidFile.writeText(pid.toString())
        } catch (_: Exception) {}
    }

    private fun killOrphan() {
        try {
            if (!pidFile.exists()) return
            val pid = pidFile.readText().trim().toIntOrNull() ?: return
            pidFile.delete()
            android.os.Process.killProcess(pid)
            Thread.sleep(150)   // brief wait for port to be released
            Log.d(TAG, "Killed orphaned proxy PID $pid")
        } catch (_: Exception) {}
    }
}
