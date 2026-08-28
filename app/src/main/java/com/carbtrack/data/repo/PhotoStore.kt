package com.carbtrack.data.repo

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
import java.util.UUID

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
    }

    private fun dir(name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    val catalogDir: File get() = dir("photos")
    val adhocDir: File get() = dir("adhoc")
    val shareDir: File get() = dir("share")

    fun newShareFile(): File = File(shareDir, "share-${UUID.randomUUID()}.jpg")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun fileFor(path: String?): File? = path?.let { File(it) }?.takeIf { it.exists() }

    /** Copies [source] into the catalog directory, downscaled. Returns the stored path. */
    suspend fun importCatalogPhoto(source: Uri): String? =
        importScaled(source, catalogDir, CATALOG_MAX_EDGE)

    /** Copies [source] into the ad-hoc directory, downscaled. Returns the stored path. */
    suspend fun importAdHocPhoto(source: Uri): String? =
        importScaled(source, adhocDir, ADHOC_MAX_EDGE)

    private suspend fun importScaled(source: Uri, target: File, maxEdge: Int): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeScaled(source, maxEdge) ?: return@runCatching null
                val out = File(target, "img-${UUID.randomUUID()}.jpg")
                FileOutputStream(out).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                }
                bitmap.recycle()
                out.absolutePath
            }.getOrNull()
        }

    fun delete(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    /** Removes share scratch files; safe to call on every app start. */
    fun clearShareCache() {
        runCatching { shareDir.listFiles()?.forEach { it.delete() } }
    }

    private fun decodeScaled(uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val rotated = applyExifRotation(uri, decoded)
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

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
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
