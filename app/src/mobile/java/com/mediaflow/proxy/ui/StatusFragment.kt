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
                return@setOnClickListener
            }
            // The proxy is useless without a battery-optimisation exemption:
            // as soon as the user backgrounds the app to play a stream in a
            // third-party video player, Android's Doze kills the proxy's
            // long-lived TCP sockets (Telegram MTProto, HLS segment fetches)
            // after ~15 s and playback stalls.  Require the exemption before
            // starting — the user can cancel, but we don't start the proxy
            // in that case.
            if (com.mediaflow.proxy.BatteryOptimization.isIgnored(requireContext())) {
                startProxyNow(svc)
            } else {
                requireBatteryExemption(svc)
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

    private fun startProxyNow(svc: com.mediaflow.proxy.ProxyService?) {
        if (svc != null) {
            svc.startProxy()
        } else {
            requireContext().startForegroundService(
                android.content.Intent(requireContext(), com.mediaflow.proxy.ProxyService::class.java)
            )
        }
    }

    /**
     * Hard requirement: without the battery-optimisation exemption Android
     * kills proxy sockets the moment the user backgrounds the app to play
     * a stream in a video player, so we refuse to start the proxy until the
     * exemption is granted.  Tapping "Grant" opens the system dialog and,
     * on return, re-checks status — if the user approved, start immediately;
     * if denied, leave the proxy stopped with a Toast explaining why.
     */
    private fun requireBatteryExemption(svc: com.mediaflow.proxy.ProxyService?) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission required")
            .setMessage(
                "This app must be exempted from battery optimization to keep " +
                    "its network connections alive when another app (e.g. your " +
                    "video player) is in the foreground. Without this, streams " +
                    "will stall within seconds."
            )
            .setCancelable(false)
            .setPositiveButton("Grant") { _, _ ->
                awaitingBatteryExemptionForSvc = svc
                com.mediaflow.proxy.BatteryOptimization.requestExemption(requireContext())
            }
            .setNegativeButton("Cancel") { _, _ ->
                android.widget.Toast.makeText(
                    requireContext(),
                    "Proxy not started — battery exemption is required.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    /** Populated while the system settings page is open; checked in onResume
     *  to decide whether the user actually granted the exemption. */
    private var awaitingBatteryExemptionForSvc: com.mediaflow.proxy.ProxyService? = null

    override fun onResume() {
        super.onResume()
        val svc = awaitingBatteryExemptionForSvc ?: return
        awaitingBatteryExemptionForSvc = null
        if (com.mediaflow.proxy.BatteryOptimization.isIgnored(requireContext())) {
            startProxyNow(svc)
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                "Battery exemption was not granted — proxy not started.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
