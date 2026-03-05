package com.baak.astronode

import android.app.Application
import com.baak.astronode.core.constants.AppConstants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        configureFirestoreOffline()
    }

    private fun configureFirestoreOffline() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(AppConstants.FIRESTORE_CACHE_SIZE_BYTES)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
