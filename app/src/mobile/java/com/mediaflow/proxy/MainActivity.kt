package com.mediaflow.proxy

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediaflow.proxy.databinding.ActivityMainBinding
import com.mediaflow.proxy.ui.ConfigFragment
import com.mediaflow.proxy.ui.MainViewModel
import com.mediaflow.proxy.ui.MetricsFragment
import com.mediaflow.proxy.ui.StatusFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mediaflow.proxy.UpdateChecker
import com.mediaflow.proxy.UpdateInstaller
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val vm: MainViewModel by viewModels()

    var proxyService: ProxyService? = null
        private set

    private var logCollectJob: Job? = null

    private val quitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishAffinity()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as ProxyService.LocalBinder).service
            proxyService = svc

            logCollectJob?.cancel()
            logCollectJob = lifecycleScope.launch {
                launch { svc.proxyManager.logs.collect { line -> vm.appendLog(line) } }
                launch { svc.proxyManager.isRunningFlow.collect { running -> vm.setRunning(running) } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            proxyService = null
            logCollectJob?.cancel()
            logCollectJob = null
            vm.setRunning(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                      Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.setPadding(bars.left, bars.top, bars.right, 0)
            binding.bottomNav.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        val serviceIntent = Intent(this, ProxyService::class.java)
        lifecycleScope.launch {
            val cfg = com.mediaflow.proxy.ConfigRepository(this@MainActivity).config.first()
            if (cfg.autoStart) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
            bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        }

        val fragments = mapOf<Int, () -> Fragment>(
            R.id.nav_status  to { StatusFragment() },
            R.id.nav_config  to { ConfigFragment() },
            R.id.nav_metrics to { MetricsFragment() },
        )
        binding.bottomNav.setOnItemSelectedListener { item ->
            val factory = fragments[item.itemId] ?: return@setOnItemSelectedListener false
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, factory())
                .commit()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_status
        }

        checkForUpdatesSilent()
    }

    private fun checkForUpdatesSilent() {
        lifecycleScope.launch {
            val info = UpdateChecker.check(this@MainActivity, force = false) ?: return@launch
            if (!isFinishing) showUpdateDialog(info)
        }
    }

    internal fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Update available — ${info.version}")
            .setMessage("A new version of MediaFlow Proxy is available. Download and install now?")
            .setPositiveButton("Download") { _, _ ->
                // Navigate to Config tab so the user sees download progress there
                binding.bottomNav.selectedItemId = R.id.nav_config
            }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, quitReceiver,
            IntentFilter(ProxyService.ACTION_QUIT_APP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(quitReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        logCollectJob?.cancel()
        try { unbindService(serviceConnection) } catch (_: Exception) {}
        super.onDestroy()
    }
}
