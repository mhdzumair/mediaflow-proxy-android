package com.mediaflow.proxy.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.MainActivity
import com.mediaflow.proxy.databinding.FragmentStatusBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.isRunning, vm.config) { running, cfg -> running to cfg }.collect { (running, cfg) ->
                    binding.btnToggle.text = if (running) "Stop" else "Start"

                    binding.statusDot.background?.mutate()?.setTint(
                        if (running) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                    )

                    binding.tvStatus.text = if (running) "Running on port ${cfg.port}" else "Stopped"

                    binding.btnOpenBrowser.isEnabled = running
                    binding.btnOpenBrowser.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${cfg.port}")))
                    }
                }
            }
        }

        // Logs
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.logs.collect { lines ->
                    binding.tvLogs.text = lines.joinToString("\n")
                    binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }

        binding.btnToggle.setOnClickListener {
            val svc = (activity as? MainActivity)?.proxyService
            if (vm.isRunning.value) {
                svc?.stopProxy()
            } else {
                if (svc != null) {
                    svc.startProxy()
                } else {
                    requireContext().startForegroundService(
                        android.content.Intent(requireContext(), com.mediaflow.proxy.ProxyService::class.java)
                    )
                }
            }
        }

        binding.btnClearLogs.setOnClickListener { vm.clearLogs() }

        binding.btnExportLogs.setOnClickListener {
            val text = vm.logs.value.joinToString("\n")
            if (text.isBlank()) return@setOnClickListener
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "MediaFlow Proxy Light Logs")
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Export Logs"
            ))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
