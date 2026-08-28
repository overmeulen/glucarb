package com.carbtrack.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbtrack.data.repo.AppSettings
import com.carbtrack.data.repo.BackupManager
import com.carbtrack.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot outcomes of a backup operation, surfaced as a snackbar. */
sealed interface BackupEvent {
    data class Message(val text: String) : BackupEvent

    /** The database file was swapped underneath Room; the process has to restart. */
    data object RestartRequired : BackupEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val backups: BackupManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun setPrompt(value: String) {
        viewModelScope.launch { repo.setAiPrompt(value) }
    }

    fun setIdleTimeout(minutes: Int) {
        viewModelScope.launch { repo.setIdleTimeout(minutes) }
    }

    fun setAiTarget(packageName: String?, label: String?) {
        viewModelScope.launch { repo.setAiTarget(packageName, label) }
    }

    fun exportTo(target: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = backups.export(target)
            _busy.value = false
            _events.send(
                BackupEvent.Message(
                    result.fold(
                        onSuccess = { count -> "Backup saved ($count photos)" },
                        onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                    ),
                ),
            )
        }
    }

    fun importFrom(source: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = backups.import(source)
            _busy.value = false
            result.fold(
                onSuccess = { _events.send(BackupEvent.RestartRequired) },
                onFailure = {
                    _events.send(
                        BackupEvent.Message("Restore failed: ${it.message ?: "unknown error"}"),
                    )
                },
            )
        }
    }
}
