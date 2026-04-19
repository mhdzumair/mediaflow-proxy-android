package com.mediaflow.proxy.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.StateSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mediaflow.proxy.ProxyConfig
import com.mediaflow.proxy.ProxyService
import com.mediaflow.proxy.R
import com.mediaflow.proxy.ui.MainViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Leanback entry point for Android TV.  Layout:
 *   Row 0 "Dashboard"   — Status · Open URL · Logs · Metrics
 *   Row 1 "Quick Settings" — Port · API Password · Auto-Start
 *   Row 2 "Advanced"     — All Settings · Import Config · Export Config
 *
 * Cards are rendered via [TvActionPresenter] which composes a colored
 * background with a centered vector icon.  Sub-activities handle the actual
 * work: [TvSettingsActivity], [TvLogsActivity], [TvMetricsActivity].
 */
class TvMainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

    private lateinit var browse: BrowseSupportFragment
    private lateinit var rowsAdapter: ArrayObjectAdapter

    private var boundService: ProxyService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as ProxyService.LocalBinder).service
            boundService = svc
            lifecycleScope.launch {
                svc.proxyManager.isRunningFlow.collect { vm.setRunning(it) }
            }
            lifecycleScope.launch {
                svc.proxyManager.logs.collect { vm.appendLog(it) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
            vm.setRunning(false)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val ok = TvConfigIO.importFromUri(this@TvMainActivity, uri)
                if (ok) Toast.makeText(
                    this@TvMainActivity,
                    "Config imported — restart proxy to apply",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        browse = supportFragmentManager
            .findFragmentById(R.id.tv_browse_fragment) as BrowseSupportFragment
        browse.title = getString(R.string.app_name)
        browse.brandColor = Color.parseColor("#0F1419")

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        browse.adapter = rowsAdapter

        browse.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            (item as? TvAction)?.let(::onCardClick)
        }

        observeState()
        bindService(
            Intent(this, ProxyService::class.java), serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        try { unbindService(serviceConnection) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.isRunning, vm.config) { r, c -> r to c }.collect { (running, cfg) ->
                    browse.title =
                        if (running) "${getString(R.string.app_name)} · running on :${cfg.port}"
                        else getString(R.string.app_name)
                    rebuildRows(running, cfg)
                }
            }
        }
    }

    private fun rebuildRows(running: Boolean, cfg: ProxyConfig) {
        rowsAdapter.clear()

        // -- Row 0 : Dashboard -------------------------------------------
        val dashboard = ArrayObjectAdapter(TvActionPresenter())
        dashboard.add(
            TvAction(
                id = ActionId.TOGGLE,
                title = if (running) "Stop Proxy" else "Start Proxy",
                subtitle = if (running) "Running on :${cfg.port}" else "Tap OK to start",
                tintColor = if (running) RUNNING else START,
                iconRes = if (running) R.drawable.ic_tv_stop else R.drawable.ic_tv_play,
            )
        )
        val url = buildProxyUrl(cfg.port)
        dashboard.add(
            TvAction(ActionId.OPEN_URL, "Open Web UI", url, ACCENT, R.drawable.ic_tv_link)
        )
        dashboard.add(
            TvAction(ActionId.LOGS, "Logs", "Live proxy output", MUTED, R.drawable.ic_tv_logs)
        )
        dashboard.add(
            TvAction(ActionId.METRICS, "Metrics", "Requests · bytes · uptime", MUTED, R.drawable.ic_tv_metrics)
        )
        rowsAdapter.add(ListRow(HeaderItem(0, "Dashboard"), dashboard))

        // -- Row 1 : Quick Settings --------------------------------------
        val quick = ArrayObjectAdapter(TvActionPresenter())
        quick.add(TvAction(ActionId.PORT, "Port", cfg.port.toString(), MUTED, R.drawable.ic_tv_settings))
        quick.add(
            TvAction(
                ActionId.PASSWORD, "API Password",
                if (cfg.apiPassword.isEmpty()) "(not set)" else "••••••••",
                MUTED, R.drawable.ic_tv_settings
            )
        )
        quick.add(
            TvAction(
                ActionId.AUTO_START, "Auto-Start",
                if (cfg.autoStart) "Enabled" else "Disabled",
                if (cfg.autoStart) RUNNING else MUTED,
                R.drawable.ic_tv_settings
            )
        )
        rowsAdapter.add(ListRow(HeaderItem(1, "Quick Settings"), quick))

        // -- Row 2 : Advanced --------------------------------------------
        val advanced = ArrayObjectAdapter(TvActionPresenter())
        advanced.add(
            TvAction(ActionId.ALL_SETTINGS, "All Settings",
                "HLS · DASH · Telegram · Acestream · logging",
                ACCENT, R.drawable.ic_tv_settings)
        )
        advanced.add(
            TvAction(ActionId.IMPORT, "Import Config", "Pick a JSON file", ACCENT, R.drawable.ic_tv_import)
        )
        advanced.add(
            TvAction(ActionId.EXPORT, "Export Config", "Share current config as JSON", ACCENT, R.drawable.ic_tv_export)
        )
        rowsAdapter.add(ListRow(HeaderItem(2, "Advanced"), advanced))
    }

    private fun onCardClick(action: TvAction) {
        when (action.id) {
            ActionId.TOGGLE -> toggleProxy()
            ActionId.OPEN_URL -> lifecycleScope.launch {
                val cfg = vm.config.first()
                openWebUi(buildProxyUrl(cfg.port))
            }
            ActionId.LOGS -> startActivity(Intent(this, TvLogsActivity::class.java))
            ActionId.METRICS -> startActivity(Intent(this, TvMetricsActivity::class.java))
            ActionId.PORT -> lifecycleScope.launch {
                val current = vm.config.first().port
                androidx.leanback.app.GuidedStepSupportFragment.add(
                    supportFragmentManager, TvPortGuidedStep.create(current)
                )
            }
            ActionId.PASSWORD -> lifecycleScope.launch {
                val current = vm.config.first().apiPassword
                androidx.leanback.app.GuidedStepSupportFragment.add(
                    supportFragmentManager, TvPasswordGuidedStep.create(current)
                )
            }
            ActionId.AUTO_START -> lifecycleScope.launch {
                val repo = com.mediaflow.proxy.ConfigRepository(applicationContext)
                val cfg = repo.config.first()
                repo.save(cfg.copy(autoStart = !cfg.autoStart))
            }
            ActionId.ALL_SETTINGS -> startActivity(Intent(this, TvSettingsActivity::class.java))
            ActionId.IMPORT -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            ActionId.EXPORT -> lifecycleScope.launch {
                TvConfigIO.exportToShareSheet(this@TvMainActivity)?.let { startActivity(it) }
            }
        }
    }

    /** Build the best-guess URL to hand to the user.  LAN IP (reachable from
     *  a phone / laptop on the same network) takes precedence over 127.0.0.1
     *  since most TVs lack a browser and the user will open it on another
     *  device. */
    private fun buildProxyUrl(port: Int): String {
        val host = getLocalIpAddress() ?: "127.0.0.1"
        return "http://$host:$port"
    }

    private fun getLocalIpAddress(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull {
                !it.isLoopbackAddress &&
                    it is java.net.Inet4Address &&
                    it.hostAddress?.isNotEmpty() == true
            }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    /** Try to launch the system browser.  Many Android-TV images ship without
     *  one, so if the Intent has no handler we fall back to a dialog that
     *  shows the URL big enough to read + type on a phone. */
    private fun openWebUi(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val hasHandler = intent.resolveActivity(packageManager) != null
        if (hasHandler) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) { /* fall through */ }
        }
        showUrlDialog(url)
    }

    private fun showUrlDialog(url: String) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val text = android.widget.TextView(this).apply {
            setPadding(padding, padding, padding, padding)
            textSize = 22f
            setTextIsSelectable(true)
            setText(url)
            setTextColor(android.graphics.Color.WHITE)
        }
        // android.app.AlertDialog (framework) because the TV activity uses
        // Theme.Leanback (extends android:Theme.Material) — AppCompat's dialog
        // would crash with "not a Theme.AppCompat theme".
        android.app.AlertDialog.Builder(this)
            .setTitle("Open on another device")
            .setMessage("This TV has no browser. Open this URL from a phone or computer on the same network.")
            .setView(text)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toggleProxy() {
        val intent = Intent(this, ProxyService::class.java)
        if (vm.isRunning.value) {
            boundService?.stopProxy() ?: run { stopService(intent) }
            return
        }
        // The proxy is useless without a battery-optimisation exemption:
        // as soon as another app (video player, browser) takes the foreground,
        // Doze kills our long-lived sockets within seconds and streams stall.
        // Refuse to start until granted.
        if (com.mediaflow.proxy.BatteryOptimization.isIgnored(this)) {
            startProxyServiceNow(intent)
        } else {
            requireBatteryExemption()
        }
    }

    private fun requireBatteryExemption() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Permission required")
            .setMessage(
                "This app must be exempted from battery optimisation to keep " +
                    "its network connections alive when other apps are in the " +
                    "foreground. Without this, streams stall within seconds."
            )
            .setCancelable(false)
            .setPositiveButton("Grant") { _, _ ->
                awaitingBatteryExemption = true
                com.mediaflow.proxy.BatteryOptimization.requestExemption(this)
            }
            .setNegativeButton("Cancel") { _, _ ->
                android.widget.Toast.makeText(
                    this,
                    "Proxy not started — battery exemption is required.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            .show()
    }

    private var awaitingBatteryExemption = false

    override fun onResume() {
        super.onResume()
        if (!awaitingBatteryExemption) return
        awaitingBatteryExemption = false
        if (com.mediaflow.proxy.BatteryOptimization.isIgnored(this)) {
            startProxyServiceNow(Intent(this, ProxyService::class.java))
        } else {
            android.widget.Toast.makeText(
                this,
                "Battery exemption was not granted — proxy not started.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun startProxyServiceNow(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private companion object {
        // Card tint palette
        const val START   = 0xFF3949AB.toInt()   // indigo
        const val RUNNING = 0xFF2F7D5C.toInt()   // green
        const val MUTED   = 0xFF37474F.toInt()   // slate
        const val ACCENT  = 0xFF00838F.toInt()   // teal
    }
}

// ---------------------------------------------------------------------------
// Card model + presenter
// ---------------------------------------------------------------------------

internal enum class ActionId {
    TOGGLE, OPEN_URL, LOGS, METRICS,
    PORT, PASSWORD, AUTO_START,
    ALL_SETTINGS, IMPORT, EXPORT,
}

internal data class TvAction(
    val id: ActionId,
    val title: String,
    val subtitle: String,
    val tintColor: Int,
    @DrawableRes val iconRes: Int,
)

/**
 * BaseCardView subclass hosting [R.layout.tv_action_card].  BaseCardView
 * gives us Leanback's built-in focus-scale + elevation for free.
 */
internal class TvActionCardView(context: android.content.Context) : BaseCardView(
    context,
    /* attrs = */ null,
    /* defStyleAttr = */ androidx.leanback.R.attr.baseCardViewStyle,
) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        cardType = CARD_TYPE_MAIN_ONLY
        LayoutInflater.from(context).inflate(R.layout.tv_action_card, this, true)
    }
}

internal class TvActionPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(TvActionCardView(parent.context))

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder.view as TvActionCardView
        val action = item as? TvAction ?: return

        card.findViewById<TextView>(R.id.card_title).text = action.title
        card.findViewById<TextView>(R.id.card_subtitle).text = action.subtitle

        // Rounded-rect card background that darkens a touch when unfocused
        // and lifts to full tint + white 2dp outline when focused.
        card.background = buildCardBackground(action.tintColor)

        // Lighter-tinted circle under the icon for a proper "icon well" look.
        card.findViewById<android.view.View>(R.id.card_icon_well)
            .background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(lighten(action.tintColor, 0.28f))
            }

        val iconView = card.findViewById<ImageView>(R.id.card_icon)
        iconView.setImageResource(action.iconRes)
        ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(Color.WHITE))
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) { /* no-op */ }

    // -- helpers ------------------------------------------------------------

    private fun buildCardBackground(tint: Int): StateListDrawable {
        val radius = 32f   // px; BaseCardView also clips with its own bounds so visual radius is close to this
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(tint)
            setStroke((2 * 1.5).toInt(), Color.WHITE)
        }
        val unfocused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(darken(tint, 0.22f))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(StateSet.WILD_CARD, unfocused)
        }
    }

    private fun lighten(color: Int, fraction: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(
            0xFF,
            (r + (255 - r) * fraction).toInt(),
            (g + (255 - g) * fraction).toInt(),
            (b + (255 - b) * fraction).toInt(),
        )
    }

    private fun darken(color: Int, fraction: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(
            0xFF,
            (r * (1 - fraction)).toInt(),
            (g * (1 - fraction)).toInt(),
            (b * (1 - fraction)).toInt(),
        )
    }
}

