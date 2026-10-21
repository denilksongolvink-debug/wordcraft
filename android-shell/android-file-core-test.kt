package com.wordcraft.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Tests for the pure part of the file bridge.
 *
 * They run on the JVM (`./gradlew testDebugUnitTest` in the APK workflow), so
 * every case here is a case the shell must survive whatever the web app sends:
 * a name with a path in it, a blob that is not base64, a file that already
 * exists.
 */
class FileBridgeTest {

    // --- file names -------------------------------------------------------

    @Test
    fun `a missing name becomes the default`() {
        assertEquals(FileBridgeCore.DEFAULT_NAME, FileBridgeCore.sanitizeFileName(null))
        assertEquals(FileBridgeCore.DEFAULT_NAME, FileBridgeCore.sanitizeFileName(""))
        assertEquals(FileBridgeCore.DEFAULT_NAME, FileBridgeCore.sanitizeFileName("   "))
    }

    @Test
    fun `an ordinary name survives`() {
        assertEquals("report.pdf", FileBridgeCore.sanitizeFileName("report.pdf"))
        assertEquals("My Document.docx", FileBridgeCore.sanitizeFileName("My Document.docx"))
        assertEquals("acentuação.txt", FileBridgeCore.sanitizeFileName("acentuação.txt"))
    }

    @Test
    fun `a name never chooses a folder`() {
        assertEquals("passwd", FileBridgeCore.sanitizeFileName("../../etc/passwd"))
        assertEquals("document.docx", FileBridgeCore.sanitizeFileName("/tmp/document.docx"))
        assertEquals("evil.txt", FileBridgeCore.sanitizeFileName("..\\..\\evil.txt"))
    }

    @Test
    fun `characters the file system rejects are replaced`() {
        assertEquals("bad_name_.pdf", FileBridgeCore.sanitizeFileName("bad:name?.pdf"))
        assertEquals("a_b_c", FileBridgeCore.sanitizeFileName("a<b>c"))
        assertEquals("a_b", FileBridgeCore.sanitizeFileName("a\u0000b"))
    }

    @Test
    fun `a name made only of dots falls back to the default`() {
        assertEquals(FileBridgeCore.DEFAULT_NAME, FileBridgeCore.sanitizeFileName("..."))
        assertEquals(FileBridgeCore.DEFAULT_NAME, FileBridgeCore.sanitizeFileName("."))
    }

    @Test
    fun `a long name is cut but keeps its extension`() {
        val long = "x".repeat(500) + ".pdf"
        val cut = FileBridgeCore.sanitizeFileName(long)
        assertEquals(FileBridgeCore.MAX_NAME_LENGTH, cut.length)
        assertTrue(cut.endsWith(".pdf"))
    }

    // --- MIME types -------------------------------------------------------

    @Test
    fun `the mime type follows the extension`() {
        assertEquals("application/pdf", FileBridgeCore.mimeFor("report.pdf"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            FileBridgeCore.mimeFor("report.docx"),
        )
        assertEquals("text/html", FileBridgeCore.mimeFor("page.HTML"))
        assertEquals("image/png", FileBridgeCore.mimeFor("shot.png"))
    }

    @Test
    fun `an unknown extension is a binary file`() {
        assertEquals("application/octet-stream", FileBridgeCore.mimeFor("report"))
        assertEquals("application/octet-stream", FileBridgeCore.mimeFor("report.unknownext"))
    }

    // --- base64 -----------------------------------------------------------

    @Test
    fun `base64 decodes to the bytes it carries`() {
        assertArrayEquals("hello".toByteArray(), FileBridgeCore.decodeBase64("aGVsbG8="))
        assertArrayEquals("WordCraft".toByteArray(), FileBridgeCore.decodeBase64("V29yZENyYWZ0"))
    }

    @Test
    fun `every byte value survives the round trip`() {
        val bytes = ByteArray(256) { it.toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        assertArrayEquals(bytes, FileBridgeCore.decodeBase64(encoded))
    }

    @Test
    fun `the url safe alphabet decodes too`() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        assertArrayEquals(bytes, FileBridgeCore.decodeBase64("-_-_"))
    }

    @Test
    fun `line breaks in the payload are ignored`() {
        assertArrayEquals("hello".toByteArray(), FileBridgeCore.decodeBase64("aGVs\nbG8="))
    }

    @Test
    fun `data that is not base64 is refused`() {
        assertNull(FileBridgeCore.decodeBase64(null))
        assertNull(FileBridgeCore.decodeBase64(""))
        assertNull(FileBridgeCore.decodeBase64("   "))
        assertNull(FileBridgeCore.decodeBase64("not base64!"))
        assertNull(FileBridgeCore.decodeBase64("aGVsbG8=x"))
        assertNull(FileBridgeCore.decodeBase64("a"))
    }

    @Test
    fun `a decoded payload is never null for real data`() {
        assertNotNull(FileBridgeCore.decodeBase64("aGVsbG8="))
    }

    // --- unique names -----------------------------------------------------

    @Test
    fun `a free name is used as it is`() {
        assertEquals("report.pdf", FileBridgeCore.uniqueFileName("report.pdf") { false })
    }

    @Test
    fun `a taken name is numbered like the browser numbers its downloads`() {
        val taken = setOf("report.pdf", "report (2).pdf")
        assertEquals("report (3).pdf", FileBridgeCore.uniqueFileName("report.pdf") { it in taken })
    }

    @Test
    fun `a name without an extension is numbered too`() {
        assertEquals("report (2)", FileBridgeCore.uniqueFileName("report") { it == "report" })
    }

    @Test
    fun `the search for a free name ends`() {
        var calls = 0
        val name = FileBridgeCore.uniqueFileName("report.pdf") { calls++; true }
        assertTrue("the search must not spin forever", calls < 20_000)
        assertTrue(name.startsWith("report"))
    }
}
