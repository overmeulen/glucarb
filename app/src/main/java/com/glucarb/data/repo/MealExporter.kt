package com.glucarb.data.repo

import android.content.Context
import android.net.Uri
import com.glucarb.domain.MealCsvExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What an export produced, so the UI can report it honestly. */
data class ExportResult(val meals: Int)

/**
 * Writes logged meals out as CSV.
 *
 * Separate from [BackupManager] on purpose: a backup is an opaque archive meant to be
 * restored into this app, while this is a readable extract meant to be handed to
 * something else. Conflating them would give the user one button that does neither job
 * well.
 */
class MealExporter(
    private val context: Context,
    private val meals: MealRepository,
    private val photos: PhotoStore,
) {

    /** Writes meals started at or after [from] into [target]. */
    suspend fun exportTo(target: Uri, from: Long): Result<ExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (csv, result) = render(from)
                val stream = context.contentResolver.openOutputStream(target)
                    ?: error("Cannot open the destination file")
                stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                result
            }
        }

    /**
     * Writes the same CSV to a temporary file and returns a shareable URI.
     *
     * Reuses the share directory, which is already declared in the FileProvider paths and
     * already swept by age, so an exported extract cannot linger indefinitely.
     */
    suspend fun exportForSharing(from: Long): Result<Pair<Uri, ExportResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (csv, result) = render(from)
                val file = File(photos.shareDir, MealCsvExport.fileName(from))
                file.writeText(csv, Charsets.UTF_8)
                photos.uriFor(file) to result
            }
        }

    private suspend fun render(from: Long): Pair<String, ExportResult> {
        val rows = meals.mealsSince(from)
        return MealCsvExport.build(rows) to ExportResult(MealCsvExport.rowCount(rows))
    }
}
