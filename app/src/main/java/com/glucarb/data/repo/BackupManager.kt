package com.glucarb.data.repo

import android.content.Context
import android.net.Uri
import com.glucarb.data.CarbDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manual export / import of the whole app state as a single zip.
 *
 * Android Auto Backup is the primary safety net, but it is silent, tied to a Google
 * account and capped at 25 MB. This is the escape hatch: it produces a file the user
 * owns, and it is the only way to move data to a phone signed into another account.
 *
 * The archive contains the Room database file plus the catalog photo directory.
 * Ad-hoc AI plate photos are deliberately left out, exactly like in `backup_rules.xml`.
 */
class BackupManager(
    private val context: Context,
    private val database: CarbDatabase,
    private val photos: PhotoStore,
) {

    companion object {
        private const val DB_ENTRY = "database/${CarbDatabase.NAME}"
        private const val PHOTO_PREFIX = "photos/"
        const val MIME_TYPE = "application/zip"

        fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
            val stamp = android.text.format.DateFormat.format("yyyyMMdd-HHmm", now)
            return "Glucarb-backup-$stamp.zip"
        }
    }

    /** Writes a backup archive into [target]. Returns the number of photos included. */
    suspend fun export(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            checkpoint()
            var photoCount = 0
            val output = context.contentResolver.openOutputStream(target)
                ?: error("Cannot open the destination file")

            ZipOutputStream(output.buffered()).use { zip ->
                val dbFile = context.getDatabasePath(CarbDatabase.NAME)
                require(dbFile.exists()) { "No database to export yet" }
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                photos.catalogDir.listFiles()?.forEach { photo ->
                    if (!photo.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + photo.name))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    photoCount++
                }
            }
            photoCount
        }
    }

    /**
     * Replaces the current database and catalog photos with the contents of [source].
     *
     * Photo paths are stored as absolute paths, and the app's data directory can differ
     * between installs, so rows are rewritten to point at the restored files afterwards.
     * The caller must restart the process: Room keeps the old file handle open.
     */
    suspend fun import(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "restore").apply {
                deleteRecursively()
                mkdirs()
            }
            var sawDatabase = false

            context.contentResolver.openInputStream(source)?.buffered()?.let { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val out = when {
                            name == DB_ENTRY -> {
                                sawDatabase = true
                                File(staging, "db")
                            }

                            name.startsWith(PHOTO_PREFIX) && !entry.isDirectory ->
                                File(staging, "photos").apply { mkdirs() }
                                    .let { File(it, File(name).name) }

                            else -> null
                        }
                        if (out != null) {
                            require(out.canonicalPath.startsWith(staging.canonicalPath)) {
                                "Refusing a backup entry that escapes the staging directory"
                            }
                            out.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Cannot read the backup file")

            require(sawDatabase) { "This zip is not a Glucarb backup" }

            checkpoint()
            database.close()

            val dbFile = context.getDatabasePath(CarbDatabase.NAME)
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
            File(staging, "db").copyTo(dbFile, overwrite = true)

            photos.catalogDir.listFiles()?.forEach { it.delete() }
            File(staging, "photos").listFiles()?.forEach { restored ->
                restored.copyTo(File(photos.catalogDir, restored.name), overwrite = true)
            }
            rewritePhotoPaths(dbFile)

            staging.deleteRecursively()
            Unit
        }
    }

    /**
     * Photo paths are stored absolute, and the app data directory can differ between
     * installs, so every catalog row is re-pointed at the freshly restored file. Rows
     * whose photo is missing from the archive lose the photo rather than dangle.
     */
    private fun rewritePhotoPaths(dbFile: File) {
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        db.use { handle ->
            val updates = mutableListOf<Pair<Long, String?>>()
            handle.rawQuery(
                "SELECT id, photoPath FROM food_items WHERE photoPath IS NOT NULL",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1).substringAfterLast('/').substringAfterLast('\\')
                    val restored = File(photos.catalogDir, name)
                    updates += id to restored.absolutePath.takeIf { restored.exists() }
                }
            }
            handle.beginTransaction()
            try {
                updates.forEach { (id, path) ->
                    handle.execSQL(
                        "UPDATE food_items SET photoPath = ? WHERE id = ?",
                        arrayOf<Any?>(path, id),
                    )
                }
                handle.setTransactionSuccessful()
            } finally {
                handle.endTransaction()
            }
        }
    }

    /** Folds the write-ahead log back into the main file so the copy is complete. */
    private fun checkpoint() {
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }
    }
}
