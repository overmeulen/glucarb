package com.carbtrack.ui.item

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbtrack.data.MeasurementUnit
import com.carbtrack.data.entity.FoodItem
import com.carbtrack.data.repo.FoodRepository
import com.carbtrack.data.repo.PhotoStore
import com.carbtrack.domain.CarbMath
import com.carbtrack.domain.EmojiSuggester
import com.carbtrack.domain.FoodIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val photos: PhotoStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditState())
    val state: StateFlow<ItemEditState> = _state

    private val itemId: Long = savedStateHandle.get<String>("itemId")?.toLongOrNull() ?: 0L

    init {
        val prefill = savedStateHandle.get<String>("name").orEmpty()
        if (itemId > 0L) {
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
        } else if (prefill.isNotBlank()) {
            _state.value = _state.value.withName(prefill)
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
            val path = photos.importCatalogPhoto(uri) ?: return@launch
            val current = _state.value
            current.discardUnsavedPhoto()
            _state.value = current.copy(photoPath = path, emoji = null, emojiTouched = true)
        }
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
            repo.save(item)
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
