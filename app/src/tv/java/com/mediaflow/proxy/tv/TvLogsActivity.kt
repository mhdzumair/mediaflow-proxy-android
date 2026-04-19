package com.mediaflow.proxy.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.ProxyService
import com.mediaflow.proxy.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live log viewer for the TV flavor.  Binds directly to [ProxyService]'s
 * [com.mediaflow.proxy.ProxyManager.logs] (replay=500) so the full recent
 * history shows up immediately on launch — no cross-activity VM sharing
 * needed.
 */
class TvLogsActivity : FragmentActivity() {

    private lateinit var logsText: TextView
    private lateinit var logsScroll: ScrollView
    private val buffer = mutableListOf<String>()
    private var collectJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as ProxyService.LocalBinder).service
            collectJob?.cancel()
            collectJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    svc.proxyManager.logs.collect { line ->
                        buffer.add(line)
                        if (buffer.size > 500) buffer.removeAt(0)
                        render()
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            collectJob?.cancel()
            collectJob = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_logs)

        logsText = findViewById(R.id.logs_text)
        logsScroll = findViewById(R.id.logs_scroll)
        findViewById<TextView>(R.id.logs_title).text = "Proxy Logs"

        findViewById<Button>(R.id.logs_clear).setOnClickListener {
            buffer.clear()
            render()
        }
        findViewById<Button>(R.id.logs_export).setOnClickListener { exportLogs() }

        bindService(
            Intent(this, ProxyService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        try { unbindService(serviceConnection) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun render() {
        logsText.text = if (buffer.isEmpty()) "(no logs — start the proxy)"
                        else buffer.joinToString("\n")
        logsScroll.post { logsScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private val shareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    private fun exportLogs() {
        val text = buffer.joinToString("\n")
        if (text.isBlank()) {
            Toast.makeText(this, "No logs to export", Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "mediaflow-logs-$stamp.txt").apply { writeText(text) }
        val uri: Uri = FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MediaFlow Proxy logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            shareLauncher.launch(Intent.createChooser(send, "Export logs"))
        } catch (_: Exception) {
            Toast.makeText(this, "No share target available on this device", Toast.LENGTH_LONG).show()
        }
    }
}
