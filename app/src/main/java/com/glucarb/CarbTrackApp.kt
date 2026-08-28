package com.glucarb

import android.app.Application
import com.glucarb.data.repo.PhotoStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GlucarbApp : Application() {

    @Inject lateinit var photoStore: PhotoStore

    override fun onCreate() {
        super.onCreate()
        // Scratch files handed to the AI app are never needed across launches.
        photoStore.clearShareCache()
    }
}
