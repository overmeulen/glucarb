package com.glucarb

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucarb.data.repo.PhotoStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Covers importing a photo the app captured itself.
 *
 * A camera capture lands in our own files/share, and the app used to read it back out
 * through FileProvider - which meant a failure anywhere in that round trip looked exactly
 * like an unreadable image. These pin the direct read and the two failure answers.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhotoCaptureTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val photos = PhotoStore(context)

    private fun writeJpeg(target: File) {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    @Test
    fun `a captured file is imported without going through the content resolver`() = runTest {
        val capture = photos.newShareFile()
        writeJpeg(capture)

        val stored = photos.importCatalogPhoto(capture)

        assertNotNull("a readable capture must import", stored)
        assertTrue(File(requireNotNull(stored)).exists())
    }

    @Test
    fun `an empty capture file is reported rather than decoded`() {
        val capture = photos.newShareFile()
        capture.createNewFile()

        assertFalse("a zero-byte capture is not content", photos.hasContent(capture))
    }

    @Test
    fun `an imported photo is stored in the catalog directory, not left in share`() = runTest {
        val capture = photos.newShareFile()
        writeJpeg(capture)

        val stored = requireNotNull(photos.importCatalogPhoto(capture))

        assertEquals(photos.catalogDir, File(stored).parentFile)
    }

    @Test
    fun `the share sweep spares a capture that was just taken`() {
        val fresh = photos.newShareFile().apply { writeText("x") }
        val old = photos.newShareFile().apply {
            writeText("x")
            setLastModified(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        }

        photos.clearShareCache()

        assertTrue("a capture in flight must survive an app restart", fresh.exists())
        assertFalse("a day-old scratch file is rubbish", old.exists())
    }
}
