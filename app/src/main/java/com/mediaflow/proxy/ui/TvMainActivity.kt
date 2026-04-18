package com.mediaflow.proxy.ui

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.ConfigRepository
import com.mediaflow.proxy.ProxyService
import com.mediaflow.proxy.R
import kotlinx.coroutines.launch
import java.net.NetworkInterface

/**
 * Android TV entry point. Uses Leanback BrowseSupportFragment with two rows:
 *   Row 0 — Status actions (Start / Stop)
 *   Row 1 — Settings (open config fragment overlay)
 */
class TvMainActivity : FragmentActivity() {

    private lateinit var browseFragment: BrowseSupportFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        browseFragment = supportFragmentManager
            .findFragmentById(R.id.tv_browse_fragment) as BrowseSupportFragment

        browseFragment.title = "MediaFlow Proxy"

        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        // --- Row 0: Status ---
        val statusAdapter = ArrayObjectAdapter(TvActionPresenter())
        statusAdapter.add(TvAction("Start Proxy", "Start the proxy service"))
        statusAdapter.add(TvAction("Stop Proxy",  "Stop the proxy service"))
        rowsAdapter.add(ListRow(HeaderItem(0, "Control"), statusAdapter))

        // --- Row 1: Settings ---
        val settingsAdapter = ArrayObjectAdapter(TvActionPresenter())
        settingsAdapter.add(TvAction("Port", "Change listen port"))
        settingsAdapter.add(TvAction("API Password", "Set API password"))
        settingsAdapter.add(TvAction("Auto-Start", "Toggle auto-start on boot"))
        rowsAdapter.add(ListRow(HeaderItem(1, "Settings"), settingsAdapter))

        browseFragment.adapter = rowsAdapter

        browseFragment.onItemViewClickedListener = OnItemViewClickedListener {
                _: Presenter.ViewHolder?, item: Any?,
                _: RowPresenter.ViewHolder?, _: androidx.leanback.widget.Row? ->
            val action = item as? TvAction ?: return@OnItemViewClickedListener
            handleTvAction(action.title)
        }

        observeStatus()
    }

    private fun observeStatus() {
        val repo = ConfigRepository(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.config.collect { cfg ->
                    val ip = getLocalIpAddress() ?: "127.0.0.1"
                    browseFragment.badgeDrawable = null
                    browseFragment.title = "MediaFlow Proxy — http://$ip:${cfg.port}"
                }
            }
        }
    }

    private fun handleTvAction(title: String) {
        when (title) {
            "Start Proxy" -> startForegroundService(Intent(this, ProxyService::class.java))
            "Stop Proxy"  -> stopService(Intent(this, ProxyService::class.java))
            // QR code removed
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains('.') == true }
                ?.hostAddress
        } catch (_: Exception) { null }
    }
}

data class TvAction(val title: String, val description: String)

/** Minimal Leanback card presenter for TV action items. */
class TvActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val view = android.widget.TextView(parent.context).apply {
            setPadding(24, 24, 24, 24)
            textSize = 16f
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        (vh.view as android.widget.TextView).text = (item as TvAction).title
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {}
}
