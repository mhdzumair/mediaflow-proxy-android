package com.mediaflow.proxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.MainActivity
import com.mediaflow.proxy.ProxyConfig
import com.mediaflow.proxy.databinding.FragmentConfigBinding
import kotlinx.coroutines.launch

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    /** Tracks whether we've done the initial population so we don't overwrite user edits */
    private var populated = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val logLevelOptions = listOf(
        "error",
        "warn",
        "info",
        "debug",
        "mediaflow_proxy_light=debug,info",
        "mediaflow_proxy_light=trace,info",
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Log level dropdown
        val logAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, logLevelOptions)
        binding.etLogLevel.setAdapter(logAdapter)

        // Show/hide proxy URL field based on All Proxy switch
        binding.switchAllProxy.setOnCheckedChangeListener { _, checked ->
            binding.tilProxyUrl.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // Populate fields from saved config — only once on first load
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.config.collect { cfg ->
                    if (!populated) {
                        populate(cfg)
                        populated = true
                    }
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val cfg = buildConfig()
            vm.saveConfig(cfg)

            // Restart proxy if it's currently running so new config takes effect
            val svc = (activity as? MainActivity)?.proxyService
            if (svc != null && svc.proxyManager.isRunning) {
                svc.stopProxy()
                svc.startProxy()
                vm.setRunning(true)
            }

            binding.tvSaveStatus.visibility = View.VISIBLE
            view.postDelayed({ _binding?.tvSaveStatus?.visibility = View.GONE }, 2000)
        }
    }

    private fun populate(cfg: ProxyConfig) {
        val b = _binding ?: return
        b.etHost.setText(cfg.host)
        b.etPort.setText(cfg.port.toString())
        b.etApiPassword.setText(cfg.apiPassword)
        b.switchAutoStart.isChecked = cfg.autoStart
        b.etConnectTimeout.setText(cfg.connectTimeout.toString())
        b.etBufferSize.setText(cfg.bufferSizeKb.toString())
        b.switchFollowRedirects.isChecked = cfg.followRedirects
        b.switchAllProxy.isChecked = cfg.allProxy
        b.tilProxyUrl.visibility = if (cfg.allProxy) View.VISIBLE else View.GONE
        b.etProxyUrl.setText(cfg.proxyUrl)
        b.etTransportRoutes.setText(cfg.transportRoutes)
        b.etHlsPrebuffer.setText(cfg.hlsPrebufferSegments.toString())
        b.etHlsCacheTtl.setText(cfg.hlsSegmentCacheTtl.toString())
        b.etMpdDepth.setText(cfg.mpdLivePlaylistDepth.toString())
        b.switchMpdRemux.isChecked = cfg.mpdRemuxToTs
        if (cfg.telegramApiId > 0) b.etTgApiId.setText(cfg.telegramApiId.toString())
        b.etTgApiHash.setText(cfg.telegramApiHash)
        b.etTgSession.setText(cfg.telegramSessionString)
        b.etTgMaxConnections.setText(cfg.telegramMaxConnections.toString())
        b.etAcestreamHost.setText(cfg.acestreamHost)
        b.etAcestreamPort.setText(cfg.acestreamPort.toString())
        b.etAcestreamAccessToken.setText(cfg.acestreamAccessToken)
        b.etLogLevel.setText(cfg.logLevel, false)
    }

    private fun buildConfig(): ProxyConfig {
        val b = binding
        return ProxyConfig(
            port                   = b.etPort.text.toString().toIntOrNull()?.coerceIn(1024, 65535) ?: 8888,
            host                   = b.etHost.text.toString().trim().ifEmpty { "0.0.0.0" },
            apiPassword            = b.etApiPassword.text.toString().trim().ifEmpty { "mediaflow" },
            autoStart              = b.switchAutoStart.isChecked,
            connectTimeout         = b.etConnectTimeout.text.toString().toIntOrNull() ?: 30,
            bufferSizeKb           = b.etBufferSize.text.toString().toIntOrNull() ?: 256,
            followRedirects        = b.switchFollowRedirects.isChecked,
            allProxy               = b.switchAllProxy.isChecked,
            proxyUrl               = b.etProxyUrl.text.toString().trim(),
            transportRoutes        = b.etTransportRoutes.text.toString().trim(),
            hlsPrebufferSegments   = b.etHlsPrebuffer.text.toString().toIntOrNull() ?: 5,
            hlsSegmentCacheTtl     = b.etHlsCacheTtl.text.toString().toIntOrNull() ?: 300,
            mpdLivePlaylistDepth   = b.etMpdDepth.text.toString().toIntOrNull() ?: 8,
            mpdRemuxToTs           = b.switchMpdRemux.isChecked,
            telegramApiId          = b.etTgApiId.text.toString().toIntOrNull() ?: 0,
            telegramApiHash        = b.etTgApiHash.text.toString().trim(),
            telegramSessionString  = b.etTgSession.text.toString().trim(),
            telegramMaxConnections = b.etTgMaxConnections.text.toString().toIntOrNull() ?: 8,
            acestreamHost          = b.etAcestreamHost.text.toString().trim().ifEmpty { "127.0.0.1" },
            acestreamPort          = b.etAcestreamPort.text.toString().toIntOrNull() ?: 6878,
            acestreamAccessToken   = b.etAcestreamAccessToken.text.toString().trim(),
            logLevel               = b.etLogLevel.text.toString().trim().ifEmpty { "info" },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
