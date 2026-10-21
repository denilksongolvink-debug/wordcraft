package com.wordcraft.app

import java.io.ByteArrayOutputStream

/**
 * The parts of the file bridge that need no Android API: file name sanitising,
 * MIME lookup, base64 decoding and unique naming.
 *
 * They live apart from the plugin so the unit tests can run them on a plain
 * JVM (`./gradlew testDebugUnitTest`); everything that touches the file system
 * stays in [FileBridge].
 *
 * Every function here takes host input (a name or a blob that came from the
 * web view) and never throws: a broken input becomes `null`, not an exception.
 */
object FileBridgeCore {

    /** Used when the web app sends no name at all. */
    const val DEFAULT_NAME = "document"

    /** The folder under Downloads the bridge writes to. */
    const val DOWNLOAD_FOLDER = "WordCraft"

    /** Longest file name we write; the stem is cut, the extension is kept. */
    const val MAX_NAME_LENGTH = 120

    /** Characters no file system accepts, and that could escape the folder. */
    private const val ILLEGAL_CHARACTERS = "<>:\"/\\|?*"

    /** Longest extension we bother keeping when the name is too long. */
    private const val MAX_EXTENSION_LENGTH = 16

    private val MIME_BY_EXTENSION = mapOf(
        "pdf" to "application/pdf",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "docm" to "application/vnd.ms-word.document.macroEnabled.12",
        "dotx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "odt" to "application/vnd.oasis.opendocument.text",
        "rtf" to "application/rtf",
        "html" to "text/html",
        "htm" to "text/html",
        "md" to "text/plain",
        "txt" to "text/plain",
        "tex" to "application/x-tex",
        "json" to "application/json",
        "xml" to "application/xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
    )

    /**
     * Turns a name from the web into a name a file system accepts.
     *
     * The name is host input: it may carry directories (`../`), characters the
     * file system rejects, or nothing at all. The result never holds a path
     * separator, so the caller can drop it inside a folder it controls.
     */
    fun sanitizeFileName(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_NAME

        // Keep the file name only: a name from the web never chooses a folder.
        val base = trimmed.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString(base.length) {
            for (character in base) {
                if (character.isISOControl() || character in ILLEGAL_CHARACTERS) {
                    append('_')
                } else {
                    append(character)
                }
            }
        }.trim().trimEnd('.', ' ')

        if (cleaned.isEmpty()) return DEFAULT_NAME
        return limitLength(cleaned, MAX_NAME_LENGTH)
    }

    /** The MIME type that goes with `name`, or a generic binary type. */
    fun mimeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    /**
     * Decodes standard or URL-safe base64, with or without padding.
     *
     * Returns `null` when the input is not base64, so the caller can tell
     * "no data" from "broken data" without catching anything.
     */
    fun decodeBase64(data: String?): ByteArray? {
        if (data == null) return null

        val out = ByteArrayOutputStream(data.length / 4 * 3 + 3)
        var buffer = 0
        var bits = 0
        var symbols = 0
        var padding = 0

        for (character in data) {
            if (character == '\n' || character == '\r' || character == ' ' || character == '\t') continue
            if (character == '=') {
                padding++
                continue
            }
            if (padding > 0) return null // a symbol after the padding
            val value = valueOf(character)
            if (value < 0) return null
            symbols++
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }

        if (symbols == 0) return null // nothing to save
        if (padding > 2) return null
        if (padding > 0 && (symbols + padding) % 4 != 0) return null
        if (symbols % 4 == 1) return null // one symbol cannot carry a byte
        return out.toByteArray()
    }

    /**
     * The first name that is free: `report.pdf`, `report (2).pdf`, `report (3).pdf`…
     *
     * `taken` says whether a name is already used. The browser names the files
     * it downloads the same way, and WordCraft keeps that habit.
     */
    fun uniqueFileName(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name

        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0) name.substring(dot) else ""
        val stem = name.substring(0, name.length - extension.length)

        var counter = 2
        while (counter < 10_000) {
            val candidate = "$stem ($counter)$extension"
            if (!taken(candidate)) return candidate
            counter++
        }
        // Never spin forever: hand back a name that is probably free.
        return "$stem ($counter)$extension"
    }

    /** Keeps the extension when the name has to be cut short. */
    private fun limitLength(name: String, max: Int): String {
        if (name.length <= max) return name

        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && name.length - dot <= MAX_EXTENSION_LENGTH) name.substring(dot) else ""
        val stem = name.substring(0, name.length - extension.length)
        return stem.take((max - extension.length).coerceAtLeast(1)) + extension
    }

    /** The six bits a base64 character carries, or -1 when it carries none. */
    private fun valueOf(character: Char): Int = when (character) {
        in 'A'..'Z' -> character - 'A'
        in 'a'..'z' -> character - 'a' + 26
        in '0'..'9' -> character - '0' + 52
        '+', '-' -> 62
        '/', '_' -> 63
        else -> -1
    }
}
