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

    /**
     * True if this package is already on the Doze whitelist, OR if the device
     * doesn't expose battery-optimisation settings at all (e.g. Amazon Fire TV).
     * On devices like Fire TV, neither battery settings intent resolves — there
     * is no Doze / battery management surface, so no exemption is needed.
     */
    fun isIgnored(ctx: Context): Boolean {
        if (!canRequestExemption(ctx)) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * Returns false on devices (e.g. Fire TV) where neither the direct exemption
     * dialog nor the battery settings screen is available.  When false the caller
     * should skip the permission requirement entirely.
     */
    fun canRequestExemption(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return pm.resolveActivity(direct, 0) != null || pm.resolveActivity(fallback, 0) != null
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
