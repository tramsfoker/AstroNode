package com.baak.astronode

import android.app.Application
import com.baak.astronode.core.appcheck.AppCheckInstaller
import com.baak.astronode.core.constants.AppConstants
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // App Check, Firestore'a ilk istekten önce kurulmalı
        AppCheckInstaller.install()
        // Debug derlemelerde çökme raporu gönderilmez
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        configureFirestoreOffline()
    }

    private fun configureFirestoreOffline() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(AppConstants.FIRESTORE_CACHE_SIZE_BYTES)
                    .build()
            )
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
