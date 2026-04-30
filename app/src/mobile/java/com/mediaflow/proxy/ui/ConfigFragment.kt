package com.mediaflow.proxy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mediaflow.proxy.ConfigRepository
import com.mediaflow.proxy.ConfigSerializer
import com.mediaflow.proxy.MainActivity
import com.mediaflow.proxy.ProxyConfig
import com.mediaflow.proxy.UpdateChecker
import com.mediaflow.proxy.UpdateInstaller
import com.mediaflow.proxy.databinding.FragmentConfigBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        binding.btnImportConfig.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        binding.btnExportConfig.setOnClickListener { exportConfig() }
        binding.btnCheckUpdate.setOnClickListener { checkForUpdates() }
    }

    private fun checkForUpdates() {
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = "Checking…"
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.check(requireContext(), force = true)
            binding.btnCheckUpdate.isEnabled = true
            binding.btnCheckUpdate.text = "Check for Updates"
            if (info == null) {
                Toast.makeText(requireContext(), "You're up to date!", Toast.LENGTH_SHORT).show()
                return@launch
            }
            showUpdateDialog(info)
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update available — ${info.version}")
            .setMessage("A new version is available. Download and install now?")
            .setPositiveButton("Download") { _, _ -> startDownload(info.downloadUrl) }
            .setNegativeButton("Later", null)
            .show()
    }

    private var downloadJob: Job? = null

    private fun startDownload(url: String) {
        val ctx = requireContext()
        if (!UpdateInstaller.canInstall(ctx)) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Allow installation")
                .setMessage(
                    "To install updates, enable \"Install unknown apps\" for this app " +
                        "in the system settings, then try again."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    UpdateInstaller.requestInstallPermission(ctx)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val progressView = android.widget.TextView(ctx).apply {
            setPadding(64, 32, 64, 32)
            text = "0%"
            textSize = 16f
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Downloading update…")
            .setView(progressView)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> downloadJob?.cancel() }
            .show()

        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            val file = UpdateInstaller.download(ctx, url) { pct ->
                withContext(Dispatchers.Main) { progressView.text = "$pct%" }
            }
            dialog.dismiss()
            if (file != null) {
                UpdateInstaller.install(ctx, file)
            } else {
                Toast.makeText(ctx, "Download failed — check your connection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ----- Import / Export -------------------------------------------------

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                } ?: return@launch
                val repo = ConfigRepository(requireContext().applicationContext)
                val current = repo.config.first()
                val imported = ConfigSerializer.fromJson(text, fallback = current)
                repo.save(imported)
                populated = false   // force re-populate from the new config
                populate(imported)
                Toast.makeText(
                    requireContext(),
                    "Config imported — Save to apply to a running proxy",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cfg = buildConfig()
                val json = ConfigSerializer.toJson(cfg)
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = withContext(Dispatchers.IO) {
                    File(requireContext().cacheDir, "mediaflow-config-$stamp.json")
                        .apply { writeText(json) }
                }
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "MediaFlow Proxy config")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Export config"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
