package com.mediaflow.proxy.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mediaflow.proxy.ConfigRepository
import com.mediaflow.proxy.ConfigSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared helpers used by the TV Import / Export cards. */
object TvConfigIO {

    /** Writes the current config to cacheDir and launches the share sheet. */
    suspend fun exportToShareSheet(ctx: Context): Intent? {
        val cfg = ConfigRepository(ctx.applicationContext).config.first()
        val json = ConfigSerializer.toJson(cfg)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = withContext(Dispatchers.IO) {
            File(ctx.cacheDir, "mediaflow-config-$stamp.json").apply { writeText(json) }
        }
        val uri: Uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MediaFlow Proxy config")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Export config"
        )
    }

    /** Reads a JSON blob from a content URI and writes the parsed config. */
    suspend fun importFromUri(ctx: Context, uri: Uri): Boolean {
        return try {
            val text = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } ?: return false
            val repo = ConfigRepository(ctx.applicationContext)
            val current = repo.config.first()
            val imported = ConfigSerializer.fromJson(text, fallback = current)
            repo.save(imported)
            true
        } catch (e: Exception) {
            Toast.makeText(ctx, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
