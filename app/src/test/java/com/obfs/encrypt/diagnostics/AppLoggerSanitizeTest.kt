package com.obfs.encrypt.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerSanitizeTest {

    @Test
    fun sanitizeFileName_redactsBaseButKeepsExtension() {
        assertEquals("s….pdf", AppLogger.sanitizeFileName("secret-contract.pdf"))
        assertEquals("a…", AppLogger.sanitizeFileName("archive"))
        assertEquals("<empty>", AppLogger.sanitizeFileName(""))
    }

    @Test
    fun sanitizePath_stripsUserPath() {
        assertEquals("<path>.obfs", AppLogger.sanitizePath("/storage/emulated/0/Docs/tax.obfs"))
        assertEquals("<path>", AppLogger.sanitizePath("/data/user/0/com.obfs.encrypt/files/"))
        assertEquals("<none>", AppLogger.sanitizePath(null))
    }

    @Test
    fun ringBuffer_capturesAndLimits() {
        AppLogger.clear()
        repeat(5) { AppLogger.i("Test", "event-$it") }
        val snap = AppLogger.snapshot(10)
        assertEquals(5, snap.size)
        assertEquals("event-4", snap.first().message) // newest first
        AppLogger.clear()
        assertTrue(AppLogger.snapshot().isEmpty())
    }

    @Test
    fun eventFormat_doesNotLeakRawPaths() {
        AppLogger.clear()
        AppLogger.e(
            "Test",
            "failed at /storage/emulated/0/My Secret Folder/file.obfs",
            RuntimeException("boom at /home/user/passwords.txt")
        )
        val line = AppLogger.snapshot(1).first().formatLine(java.text.SimpleDateFormat("HH:mm:ss"))
        assertFalse(line.contains("My Secret Folder"))
        assertFalse(line.contains("passwords.txt"))
        assertTrue(line.contains("<path>"))
        AppLogger.clear()
    }
}
