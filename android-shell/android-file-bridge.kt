package com.wordcraft.app

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

/**
 * Writes the files the web app produces (Save, Save As, Export) to the device.
 *
 * The WASM app hands bytes to `Services::download` as a blob plus an `<a download>` click, which the
 * WebView ignores. The script injected by MainActivity forwards the blob here as base64 instead.
 *
 * `save` writes to the public Downloads folder (MediaStore on API 29+, the legacy public directory
 * below that). `share` writes to the cache and hands it to the system share sheet through a
 * FileProvider URI, which works on every API level.
 */
@CapacitorPlugin(name = "FileBridge")
class FileBridge : Plugin() {

    /** Writes the base64 `data` of `name` into Downloads. Returns the file uri and display name. */
    @PluginMethod
    fun save(call: PluginCall) {
        val name = call.getString("name") ?: "document"
        val bytes = decode(call.getString("data"))
        if (bytes == null) {
            call.reject("no data")
            return
        }
        try {
            val uri = writeToDownloads(name, bytes)
            val result = JSObject()
            result.put("uri", uri.toString())
            result.put("name", name)
            call.resolve(result)
        } catch (e: Exception) {
            // Capacitor 6 has no `reject(String, Exception)` overload; fold the cause into the message.
            call.reject("could not save $name: ${e.message}")
        }
    }

    /** Saves the file and then opens the system share sheet for it. */
    @PluginMethod
    fun share(call: PluginCall) {
        val name = call.getString("name") ?: "document"
        val bytes = decode(call.getString("data"))
        if (bytes == null) {
            call.reject("no data")
            return
        }
        try {
            val dir = File(activity.cacheDir, "shared")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeFor(name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share $name")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(chooser)
            call.resolve()
        } catch (e: Exception) {
            call.reject("could not share $name: ${e.message}")
        }
    }

    private fun decode(base64: String?): ByteArray? {
        if (base64.isNullOrEmpty()) return null
        return try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun writeToDownloads(name: String, bytes: ByteArray): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WordCraft")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore refused the insert")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("could not open $uri for writing")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WordCraft")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun mimeFor(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "docm" -> "application/vnd.ms-word.document.macroEnabled.12"
            "dotx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "rtf" -> "application/rtf"
            "html", "htm" -> "text/html"
            "md", "txt" -> "text/plain"
            "tex" -> "application/x-tex"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }
    }
}
