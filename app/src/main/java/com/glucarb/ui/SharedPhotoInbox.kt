package com.glucarb.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds an image shared into Glucarb from another app until an item editor takes it.
 *
 * The activity posts; navigation opens a new editor if none is showing; the editor on
 * screen consumes. In memory only: the read grant that comes with a shared URI does not
 * outlive the process, so persisting the URI would only persist a dead reference.
 */
object SharedPhotoInbox {

    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending

    fun post(uri: Uri) {
        _pending.value = uri
    }

    /** Returns the pending image, if any, and clears it so it is applied exactly once. */
    fun take(): Uri? {
        val uri = _pending.value
        _pending.value = null
        return uri
    }
}
