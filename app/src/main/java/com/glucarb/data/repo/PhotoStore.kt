package com.glucarb.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Outcome of importing a photo.
 *
 * Carries the reason on failure. An import that can only answer "no" gives the user a
 * dead button and gives us nothing to work with.
 */
sealed interface PhotoImport {
    data class Stored(val path: String) : PhotoImport
    data class Failed(val reason: String) : PhotoImport

    val pathOrNull: String? get() = (this as? Stored)?.path
}

/**
 * Stores photos inside app-internal storage.
 *
 *  - `files/photos`  catalog thumbnails, downscaled so Auto Backup's 25 MB quota holds;
 *  - `files/adhoc`   one-off AI plate photos, excluded from backup;
 *  - `files/share`   full-size captures handed to the AI app through FileProvider.
 */
class PhotoStore(private val context: Context) {

    companion object {
        /** Catalog thumbnails: ~400 px longest edge keeps each file around 30 KB. */
        const val CATALOG_MAX_EDGE = 400

        /** Ad-hoc plate photos are shown larger in history, but still bounded. */
        const val ADHOC_MAX_EDGE = 900

        /** Long enough to outlive any camera round trip, short enough to not accumulate. */
        const val SHARE_MAX_AGE_MS = 6L * 60 * 60 * 1000
    }

    /** True when a capture target actually received image bytes. */
    fun hasContent(file: File): Boolean = file.exists() && file.length() > 0L

    private fun dir(name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    val catalogDir: File get() = dir("photos")
    val adhocDir: File get() = dir("adhoc")
    val shareDir: File get() = dir("share")

    fun newShareFile(): File = File(shareDir, "share-${UUID.randomUUID()}.jpg")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun fileFor(path: String?): File? = path?.let { File(it) }?.takeIf { it.exists() }

    /** Copies [source] into the catalog directory, downscaled. */
    suspend fun importCatalogPhoto(source: Uri): PhotoImport =
        importScaled({ context.contentResolver.openInputStream(source) }, catalogDir, CATALOG_MAX_EDGE, "$source")

    /** Copies [source] into the ad-hoc directory, downscaled. */
    suspend fun importAdHocPhoto(source: Uri): PhotoImport =
        importScaled({ context.contentResolver.openInputStream(source) }, adhocDir, ADHOC_MAX_EDGE, "$source")

    /**
     * Imports a file this app owns, reading it directly.
     *
     * A camera capture lands in our own `files/share`, so going back out through
     * FileProvider and the ContentResolver to read it again adds a permission and
     * resolution path that can fail for reasons that have nothing to do with the image.
     */
    suspend fun importCatalogPhoto(source: File): PhotoImport =
        importScaled({ source.inputStream() }, catalogDir, CATALOG_MAX_EDGE, describe(source))

    suspend fun importAdHocPhoto(source: File): PhotoImport =
        importScaled({ source.inputStream() }, adhocDir, ADHOC_MAX_EDGE, describe(source))

    private fun describe(file: File): String =
        "${file.name}, ${file.length()} bytes, readable=${file.canRead()}"

    /**
     * Internal rather than private so a test can count stream opens: the bug this replaced
     * aborted after the *first* open, which no assertion about the returned bitmap could
     * catch under Robolectric's BitmapFactory shadow.
     */
    internal suspend fun importScaled(
        open: () -> InputStream?,
        target: File,
        maxEdge: Int,
        origin: String,
    ): PhotoImport =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeScaled(open, maxEdge)
                    ?: return@withContext PhotoImport.Failed("not a decodable image ($origin)")
                val out = File(target, "img-${UUID.randomUUID()}.jpg")
                FileOutputStream(out).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                }
                bitmap.recycle()
                PhotoImport.Stored(out.absolutePath)
            } catch (e: Throwable) {
                // Reported rather than swallowed: two rounds of guessing at an invisible
                // failure cost more than showing the user an ugly but truthful message.
                PhotoImport.Failed("${e.javaClass.simpleName}: ${e.message} ($origin)")
            }
        }

    fun delete(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    /**
     * Sweeps stale scratch files handed to the AI app.
     *
     * Deliberately age-based rather than "delete everything on start": launching the
     * camera can get this process killed on a low-memory phone, and an unconditional
     * sweep then deletes the photo the camera just wrote, before the result of the
     * capture has even been delivered. That looked exactly like an unreadable photo.
     */
    fun clearShareCache(now: Long = System.currentTimeMillis(), maxAgeMillis: Long = SHARE_MAX_AGE_MS) {
        runCatching {
            shareDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMillis) file.delete()
            }
        }
    }

    private fun decodeScaled(open: () -> InputStream?, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null *by design* when inJustDecodeBounds is set - it only
        // fills in the dimensions. So the null check has to be on the stream; testing the
        // decode result here rejects every photo that reads perfectly well.
        (open() ?: error("could not open the photo for reading")).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) {
            error("no image found: ${bounds.outWidth}x${bounds.outHeight}, type=${bounds.outMimeType}")
        }

        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = (open() ?: error("could not reopen the photo")).use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("decode returned nothing at sample=$sample for ${bounds.outWidth}x${bounds.outHeight}")

        val rotated = applyExifRotation(open, decoded)
        val edge = maxOf(rotated.width, rotated.height)
        if (edge <= maxEdge) return rotated

        val scale = maxEdge.toFloat() / edge
        val scaled = Bitmap.createScaledBitmap(
            rotated,
            (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== rotated) rotated.recycle()
        return scaled
    }

    private fun applyExifRotation(open: () -> InputStream?, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            open()?.use { input ->
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
