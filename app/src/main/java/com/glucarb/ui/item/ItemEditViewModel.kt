package com.glucarb.ui.item

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucarb.data.MeasurementUnit
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.PhotoImport
import com.glucarb.data.repo.PhotoStore
import com.glucarb.domain.CarbMath
import com.glucarb.domain.EmojiSuggester
import com.glucarb.domain.FoodIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemEditState(
    val id: Long = 0,
    val name: String = "",
    val photoPath: String? = null,
    val emoji: String? = null,
    /**
     * True once the user has taken charge of the visuals. While false the icon tracks whatever
     * the name suggests, which is what makes the zero-tap case work; once true we never
     * overwrite their choice.
     */
    val emojiTouched: Boolean = false,
    val suggestions: List<FoodIcon> = emptyList(),
    val unit: MeasurementUnit = MeasurementUnit.G,
    val carbsPer100: String = "",
    val portionEnabled: Boolean = false,
    val portionLabel: String = "portion",
    val portionSize: String = "",
    val originalPhotoPath: String? = null,
    val saved: Boolean = false,
) {
    val carbsValue: Double? get() = carbsPer100.replace(',', '.').toDoubleOrNull()
    val portionValue: Double? get() = portionSize.replace(',', '.').toDoubleOrNull()

    val canSave: Boolean
        get() = name.isNotBlank() &&
            (carbsValue ?: -1.0) >= 0.0 &&
            (!portionEnabled || (portionValue ?: 0.0) > 0.0)

    /** Live preview of one portion, so the user can sanity-check before saving. */
    val portionPreview: String?
        get() {
            if (!portionEnabled) return null
            val size = portionValue ?: return null
            val carbs = carbsValue ?: return null
            if (size <= 0.0) return null
            return "1 ${portionLabel.ifBlank { "portion" }} = ${CarbMath.format(size)}${unit.label}" +
                " = ${CarbMath.format(CarbMath.carbsFor(size, carbs))} g carbs"
        }
}

@HiltViewModel
class ItemEditViewModel @Inject constructor(
    private val repo: FoodRepository,
    private val meals: com.glucarb.data.repo.MealRepository,
    private val photos: PhotoStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditState())
    val state: StateFlow<ItemEditState> = _state

    private val _errors = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    private val itemId: Long = savedStateHandle.get<String>("itemId")?.toLongOrNull() ?: 0L

    /**
     * The initial load of an existing item.
     *
     * Anything that mutates state asynchronously must wait for this. Taking a photo can get
     * this process killed, and on the way back the import would otherwise finish first and
     * be overwritten wholesale when the reload landed - a photo that silently never appears.
     */
    private val loadJob: kotlinx.coroutines.Job

    init {
        val prefill = savedStateHandle.get<String>("name").orEmpty()
        loadJob = if (itemId > 0L) {
            viewModelScope.launch {
                repo.get(itemId)?.let { item ->
                    _state.value = ItemEditState(
                        id = item.id,
                        name = item.name,
                        photoPath = item.photoPath,
                        originalPhotoPath = item.photoPath,
                        emoji = item.emoji,
                        // Never re-suggest over a saved item: whatever is stored is a decision,
                        // including the decision to have no icon at all.
                        emojiTouched = true,
                        suggestions = EmojiSuggester.suggest(item.name).map { it.icon },
                        unit = item.unit,
                        carbsPer100 = CarbMath.format(item.carbsPer100, 2),
                        portionEnabled = item.portionEnabled,
                        portionLabel = item.portionLabel ?: "portion",
                        portionSize = item.portionSize?.let { CarbMath.format(it, 2) } ?: "",
                    )
                }
            }
        } else {
            if (prefill.isNotBlank()) _state.value = _state.value.withName(prefill)
            kotlinx.coroutines.CompletableDeferred(Unit)
        }
    }

    fun setName(value: String) { _state.value = _state.value.withName(value) }
    fun setUnit(value: MeasurementUnit) { _state.value = _state.value.copy(unit = value) }
    fun setCarbs(value: String) { _state.value = _state.value.copy(carbsPer100 = value.filterNumeric()) }
    fun setPortionLabel(value: String) { _state.value = _state.value.copy(portionLabel = value) }
    fun setPortionSize(value: String) { _state.value = _state.value.copy(portionSize = value.filterNumeric()) }

    fun setPortionEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(portionEnabled = enabled)
    }

    /** Chooses an icon explicitly. A photo would hide it, so the two are mutually exclusive. */
    fun setEmoji(glyph: String?) {
        val current = _state.value
        current.discardUnsavedPhoto()
        _state.value = current.copy(emoji = glyph, emojiTouched = true, photoPath = null)
    }

    fun pickPhoto(uri: Uri) {
        viewModelScope.launch {
            loadJob.join()
            when (val result = photos.importCatalogPhoto(uri)) {
                is PhotoImport.Failed -> _errors.send("Photo failed \u2014 ${result.reason}")
                is PhotoImport.Stored -> applyPhoto(result.path)
            }
        }
    }

    /** Capture target for "take a photo". The scratch file lives in files/share. */
    fun newCaptureTarget(): Pair<java.io.File, Uri> {
        val file = photos.newShareFile()
        return file to photos.uriFor(file)
    }

    /**
     * Imports a just-taken photo. The scratch file is removed once the downscaled copy
     * exists, and kept if the import failed so nothing is lost silently.
     *
     * Every failure path reports: a capture that quietly does nothing is indistinguishable
     * from a broken button, and that is exactly how the last one was found.
     */
    fun photoTaken(scratch: java.io.File) {
        viewModelScope.launch {
            loadJob.join()
            if (!photos.hasContent(scratch)) {
                _errors.send("The camera saved nothing to ${scratch.name}")
                return@launch
            }
            when (val result = photos.importCatalogPhoto(scratch)) {
                is PhotoImport.Failed -> _errors.send("Photo failed \u2014 ${result.reason}")
                is PhotoImport.Stored -> {
                    photos.delete(scratch.absolutePath)
                    applyPhoto(result.path)
                }
            }
        }
    }

    private fun applyPhoto(path: String) {
        val current = _state.value
        current.discardUnsavedPhoto()
        _state.value = current.copy(photoPath = path, emoji = null, emojiTouched = true)
    }

    /** Removing the photo hands the item back to the name-based suggestion. */
    fun clearPhoto() {
        val current = _state.value
        current.discardUnsavedPhoto()
        _state.value = current.copy(
            photoPath = null,
            emoji = EmojiSuggester.autoPick(current.name)?.glyph,
            emojiTouched = false,
        )
    }

    /**
     * Deletes a photo imported during this edit that is about to be replaced or abandoned.
     * The item's *original* photo is left alone: [save] only unlinks it once the replacement
     * has actually been persisted.
     */
    private fun ItemEditState.discardUnsavedPhoto() {
        if (photoPath != null && photoPath != originalPhotoPath) photos.delete(photoPath)
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val item = FoodItem(
                id = s.id,
                name = s.name.trim(),
                photoPath = s.photoPath,
                emoji = s.emoji,
                unit = s.unit,
                carbsPer100 = s.carbsValue ?: 0.0,
                portionEnabled = s.portionEnabled,
                portionSize = if (s.portionEnabled) s.portionValue else null,
                portionLabel = if (s.portionEnabled) s.portionLabel.trim().ifBlank { "portion" } else null,
            )
            val savedId = repo.save(item)
            // A saved edit is a correction, and the meal being assembled right now must
            // reflect it. Closed meals keep the figures they were logged with.
            meals.refreshOpenMealFor(item.copy(id = savedId))
            // A replaced photo is only unlinked once the new one is safely persisted.
            if (s.originalPhotoPath != null && s.originalPhotoPath != s.photoPath) {
                photos.delete(s.originalPhotoPath)
            }
            _state.value = s.copy(saved = true)
        }
    }

    fun archive() {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repo.archive(itemId)
            _state.value = _state.value.copy(saved = true)
        }
    }
}

private fun ItemEditState.withName(value: String): ItemEditState {
    val suggestions = EmojiSuggester.suggest(value)
    return copy(
        name = value,
        suggestions = suggestions.map { it.icon },
        // Only a confident match is applied unprompted, and only while the user has not chosen
        // for themselves. A vague name is left with the plain coloured initial.
        emoji = if (emojiTouched) emoji else suggestions.firstOrNull()?.takeIf { it.confident }?.icon?.glyph,
    )
}

private fun String.filterNumeric(): String {
    val cleaned = replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot < 0) return cleaned.take(7)
    return (cleaned.substring(0, firstDot + 1) +
        cleaned.substring(firstDot + 1).replace(".", "")).take(7)
}
