package com.mediaflow.proxy.tv

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.ConfigRepository
import com.mediaflow.proxy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Polls `http://127.0.0.1:<port>/metrics?api_password=…` every 5 s and paints
 * the result into a grid of text cards.  Reads the port/password straight
 * from [ConfigRepository] (no dependency on the service being bound), and
 * simply shows "Offline" when the fetch fails — matching the phone's
 * MetricsFragment behaviour.
 */
class TvMetricsActivity : FragmentActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_metrics)

        status = findViewById(R.id.metrics_status)

        setCardLabel(R.id.row_uptime,              "Uptime")
        setCardLabel(R.id.card_total_requests,     "Total Requests")
        setCardLabel(R.id.card_active_connections, "Active Connections")
        setCardLabel(R.id.card_bytes_out,          "Bytes Out")
        setCardLabel(R.id.card_proxy_stream,       "Proxy Stream")
        setCardLabel(R.id.card_hls,                "HLS")
        setCardLabel(R.id.card_mpd,                "DASH / MPD")
        setCardLabel(R.id.card_telegram,           "Telegram")
        setCardLabel(R.id.card_extractor,          "Extractors")

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val repo = ConfigRepository(applicationContext)
                while (true) {
                    val cfg = repo.config.first()
                    poll(cfg.port, cfg.apiPassword)
                    delay(5_000)
                }
            }
        }
    }

    private fun setCardLabel(viewId: Int, label: String) {
        findViewById<View>(viewId)
            .findViewById<TextView>(R.id.metric_label).text = label
    }

    private fun setCardValue(viewId: Int, value: String) {
        findViewById<View>(viewId)
            .findViewById<TextView>(R.id.metric_value).text = value
    }

    private suspend fun poll(port: Int, apiPassword: String) {
        val pw = URLEncoder.encode(apiPassword.ifEmpty { "mediaflow" }, "UTF-8")
        val url = "http://127.0.0.1:$port/metrics?api_password=$pw"
        val body: String? = try {
            withContext(Dispatchers.IO) { URL(url).readText() }
        } catch (_: Exception) {
            null
        }
        if (body == null) {
            status.text = "Offline"
            return
        }
        try {
            val o = JSONObject(body)
            setCardValue(R.id.row_uptime,              formatUptime(o.optLong("uptime_seconds")))
            setCardValue(R.id.card_total_requests,     o.optLong("total_requests").toString())
            setCardValue(R.id.card_active_connections, o.optLong("active_connections").toString())
            setCardValue(R.id.card_bytes_out,          o.optString("bytes_out_human", "0 B"))
            setCardValue(R.id.card_proxy_stream,       o.optLong("proxy_stream_requests").toString())
            setCardValue(R.id.card_hls,                o.optLong("hls_requests").toString())
            setCardValue(R.id.card_mpd,                o.optLong("mpd_requests").toString())
            setCardValue(R.id.card_telegram,           o.optLong("telegram_requests").toString())
            setCardValue(R.id.card_extractor,          o.optLong("extractor_requests").toString())
            status.text = "Live"
        } catch (_: Exception) {
            status.text = "Offline"
        }
    }

    private fun formatUptime(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || h > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }
}
