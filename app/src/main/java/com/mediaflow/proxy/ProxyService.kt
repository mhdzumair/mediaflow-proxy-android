package com.mediaflow.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ProxyService"

class ProxyService : Service() {

    inner class LocalBinder : Binder() {
        val service: ProxyService get() = this@ProxyService
    }

    private val binder = LocalBinder()

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Coroutine error: ${t.message}", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    lateinit var proxyManager: ProxyManager
        private set

    private lateinit var configRepo: ConfigRepository

    override fun onCreate() {
        super.onCreate()
        proxyManager = ProxyManager(applicationContext)
        configRepo = ConfigRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                updateNotification("Starting…", false)
                startProxy()
                return START_NOT_STICKY
            }
            ACTION_QUIT -> {
                try { proxyManager.stop() } catch (_: Exception) {}
                @Suppress("DEPRECATION")
                stopForeground(true)
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                stopSelf()
                sendBroadcast(Intent(ACTION_QUIT_APP).setPackage(packageName))
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting…", false))

        scope.launch {
            try {
                val cfg = configRepo.config.first()
                if (!proxyManager.isRunning) {
                    val ok = proxyManager.start(cfg)
                    updateNotification(if (ok) "Running on port ${cfg.port}" else "Failed to start", ok)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start proxy: ${t.message}", t)
                updateNotification("Error: ${t.message}", false)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try { proxyManager.stop() } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    fun stopProxy() {
        try {
            proxyManager.stop()
            updateNotification("Stopped — tap to open", false)
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping proxy: ${t.message}", t)
        }
    }

    fun startProxy() {
        scope.launch {
            try {
                val cfg = configRepo.config.first()
                val ok = proxyManager.start(cfg)
                updateNotification(if (ok) "Running on port ${cfg.port}" else "Failed to start", ok)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start proxy: ${t.message}", t)
                updateNotification("Error: ${t.message}", false)
            }
        }
    }

    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "MediaFlow Proxy background service" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String, isRunning: Boolean): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ProxyService::class.java).apply {
                action = if (isRunning) ACTION_STOP else ACTION_START
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val quitIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ProxyService::class.java).apply { action = ACTION_QUIT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MediaFlow Proxy Light")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(0, if (isRunning) "Stop" else "Start", toggleIntent)
            .addAction(0, "Quit", quitIntent)
            .build()
    }

    private fun updateNotification(status: String, isRunning: Boolean) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(status, isRunning))
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "mediaflow_proxy"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.mediaflow.proxy.ACTION_START"
        const val ACTION_STOP = "com.mediaflow.proxy.ACTION_STOP"
        const val ACTION_QUIT = "com.mediaflow.proxy.ACTION_QUIT"
        const val ACTION_QUIT_APP = "com.mediaflow.proxy.ACTION_QUIT_APP"
    }
}
