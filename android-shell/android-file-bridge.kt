package com.wordcraft.app

import android.Manifest
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File
import java.io.IOException

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

    /** What `save` reports back: where the bytes went. */
    private data class SavedFile(val uri: Uri, val name: String, val path: String)

    /** Writes the base64 `data` of `name` into Downloads. Returns the file uri and display name. */
    @PluginMethod
    fun save(call: PluginCall) {
        val name = FileBridgeCore.sanitizeFileName(call.getString("name"))
        val bytes = FileBridgeCore.decodeBase64(call.getString("data"))
        if (bytes == null) {
            call.reject("no data")
            return
        }
        val host = getActivity()
        if (host == null) {
            call.reject("no activity")
            return
        }
        try {
            val saved = writeToDownloads(host, name, bytes)
            val result = JSObject()
            result.put("uri", saved.uri.toString())
            result.put("name", saved.name)
            result.put("path", saved.path)
            call.resolve(result)
        } catch (e: Exception) {
            // Capacitor 6 has no `reject(String, Exception)` overload; fold the cause into the message.
            call.reject("could not save $name: ${e.message}")
        }
    }

    /** Saves the file and then opens the system share sheet for it. */
    @PluginMethod
    fun share(call: PluginCall) {
        val name = FileBridgeCore.sanitizeFileName(call.getString("name"))
        val bytes = FileBridgeCore.decodeBase64(call.getString("data"))
        if (bytes == null) {
            call.reject("no data")
            return
        }
        val host = getActivity()
        if (host == null) {
            call.reject("no activity")
            return
        }
        try {
            val dir = File(host.cacheDir, SHARED_FOLDER)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("could not create $dir")
            }
            val file = File(dir, name)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(host, "${host.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = FileBridgeCore.mimeFor(name)
                putExtra(Intent.EXTRA_STREAM, uri)
                // The clip data is what actually hands the read grant to the receiving app.
                clipData = ClipData.newRawUri(name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val title = host.applicationInfo.loadLabel(host.packageManager).toString()
            host.startActivity(Intent.createChooser(send, title))

            val result = JSObject()
            result.put("uri", uri.toString())
            result.put("name", file.name)
            call.resolve(result)
        } catch (e: Exception) {
            call.reject("could not share $name: ${e.message}")
        }
    }

    /** API 29+ scoped storage: MediaStore owns the Downloads folder. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(host: android.app.Activity, name: String, bytes: ByteArray): SavedFile {
        val resolver = host.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + FileBridgeCore.DOWNLOAD_FOLDER
        val displayName = FileBridgeCore.uniqueFileName(name) { candidate -> displayNameTaken(resolver, candidate, relativePath) }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, FileBridgeCore.mimeFor(displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("the media store refused $name")

        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("could not open $uri for writing")
            stream.use { it.write(bytes) }
        } catch (e: Exception) {
            // A pending entry never becomes visible and would stay on disk forever.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        return SavedFile(uri, displayName, relativePath + "/" + displayName)
    }

    /**
     * API 28 and below: write the public Downloads folder when the storage
     * permission has been granted, and the app's own external folder when it
     * has not — that folder needs no permission, so the save still happens.
     */
    @Suppress("DEPRECATION")
    private fun writeToLegacyDownloads(host: android.app.Activity, name: String, bytes: ByteArray): SavedFile {
        val public = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val canWritePublic =
            ContextCompat.checkSelfPermission(host, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val downloads = if (canWritePublic) {
            public
        } else {
            host.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: host.filesDir
        }
        val dir = File(downloads, FileBridgeCore.DOWNLOAD_FOLDER)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("could not create $dir")
        }
        val displayName = FileBridgeCore.uniqueFileName(name) { candidate -> File(dir, candidate).exists() }
        val file = File(dir, displayName)
        file.writeBytes(bytes)
        return SavedFile(Uri.fromFile(file), file.name, file.absolutePath)
    }

    private fun writeToDownloads(host: android.app.Activity, name: String, bytes: ByteArray): SavedFile {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(host, name, bytes)
        } else {
            writeToLegacyDownloads(host, name, bytes)
        }
    }

    /** Whether Downloads/WordCraft already holds a file called `name`. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun displayNameTaken(resolver: ContentResolver, name: String, relativePath: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val arguments = arrayOf(name, "$relativePath/")
        return try {
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, arguments, null)?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (e: Exception) {
            // A failing lookup must not stop the save; the worst case is an
            // extra file the media store names itself.
            false
        }
    }

    private companion object {
        /** Cache folder FileProvider exposes to the share sheet (see android-file-paths.xml). */
        const val SHARED_FOLDER = "shared"
    }
}
