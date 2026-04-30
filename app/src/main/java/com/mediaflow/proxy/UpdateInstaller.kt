package com.mediaflow.proxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {

    /**
     * Downloads the APK at [url] into the app cache directory.
     * Reports progress (0–100) on the calling dispatcher via [onProgress].
     * Returns the local [File] on success, null on any error.
     */
    suspend fun download(
        ctx: Context,
        url: String,
        onProgress: suspend (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dest = File(ctx.cacheDir, "mediaflow-update.apk")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 60_000
            conn.connect()

            val total = conn.contentLength.toLong()
            var downloaded = 0L

            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8_192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
            conn.disconnect()
            dest
        } catch (_: Exception) {
            null
        }
    }

    /** True if the app is allowed to install packages from unknown sources. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system page so the user can enable install-from-unknown-sources. */
    fun requestInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /** Hands [apkFile] to the system package installer via FileProvider. */
    fun install(ctx: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            apkFile,
        )
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
