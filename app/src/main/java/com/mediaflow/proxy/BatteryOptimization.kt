package com.mediaflow.proxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helpers around `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.  Android kills
 * long-lived TCP sockets once the app has been idle / screen-off for ~15 s
 * unless the app is whitelisted from Doze, even while a foreground-service
 * notification is showing.  That's what was causing grammers MTProto login
 * to fail mid-flow on-device.
 *
 * The user has to grant the whitelist manually — Play policy forbids apps
 * from silently exempting themselves.  We can at least detect the current
 * state and pop the system Settings page with a single tap.
 */
object BatteryOptimization {

    /** True if this package is already on the Doze whitelist. */
    fun isIgnored(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * Launch the system's "Ignore Battery Optimisations" dialog, which
     * on consent flips [isIgnored] to true.
     *
     * Using `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (not the generic
     * settings page) so the user only has to tap *Allow*; on cancel they're
     * returned to the app.
     */
    fun requestExemption(ctx: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open the global battery-optimisation settings list
            // (happens on Android TV / certain OEMs that block the direct
            // request intent).
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { /* no settings app on this image */ }
        }
    }
}
