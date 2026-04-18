package com.mediaflow.proxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.databinding.FragmentMetricsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class MetricsFragment : Fragment() {

    private var _binding: FragmentMetricsBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMetricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe running state to show/hide the "not running" notice
                vm.isRunning.collect { running ->
                    binding.tvNotRunning.visibility = if (running) View.GONE else View.VISIBLE
                }
            }
        }

        // Poll /metrics every 5 seconds while the fragment is visible
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (vm.isRunning.value) {
                        val cfg = vm.config.first()
                        fetchAndDisplay(cfg.port, cfg.apiPassword)
                    }
                    delay(5_000)
                }
            }
        }
    }

    private suspend fun fetchAndDisplay(port: Int, apiPassword: String) {
        try {
            // /metrics is authenticated like every other data endpoint —
            // pass the same api_password the app configured the proxy with.
            val pw = URLEncoder.encode(apiPassword.ifEmpty { "mediaflow" }, "UTF-8")
            val url = "http://127.0.0.1:$port/metrics?api_password=$pw"
            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                URL(url).readText()
            }
            val obj = JSONObject(json)

            binding.tvUptime.text       = formatUptime(obj.optLong("uptime_seconds"))
            binding.tvTotalRequests.text = obj.optLong("total_requests").toString()
            binding.tvActiveConnections.text = obj.optLong("active_connections").toString()
            binding.tvBytesOut.text     = obj.optString("bytes_out_human", "0 B")
            binding.tvProxyStreamReqs.text  = obj.optLong("proxy_stream_requests").toString()
            binding.tvHlsReqs.text      = obj.optLong("hls_requests").toString()
            binding.tvMpdReqs.text      = obj.optLong("mpd_requests").toString()
            binding.tvTelegramReqs.text = obj.optLong("telegram_requests").toString()
            binding.tvExtractorReqs.text= obj.optLong("extractor_requests").toString()
            binding.tvRefreshStatus.text = "Live"
        } catch (_: Exception) {
            binding.tvRefreshStatus.text = "Offline"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
