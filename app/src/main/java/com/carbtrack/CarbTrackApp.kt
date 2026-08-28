package com.carbtrack

import android.app.Application
import com.carbtrack.data.repo.PhotoStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarbTrackApp : Application() {

    @Inject lateinit var photoStore: PhotoStore

    override fun onCreate() {
        super.onCreate()
        // Scratch files handed to the AI app are never needed across launches.
        photoStore.clearShareCache()
    }
}
