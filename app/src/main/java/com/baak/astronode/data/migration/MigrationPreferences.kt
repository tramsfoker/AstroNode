package com.baak.astronode.data.migration

import android.content.Context
import android.content.SharedPreferences
import com.baak.astronode.core.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "astro_node_migration",
        Context.MODE_PRIVATE
    )

    fun isGeoHashMigrationDone(): Boolean =
        prefs.getBoolean(AppConstants.PREF_MIGRATION_GEOHASH_DONE, false)

    fun setGeoHashMigrationDone() {
        prefs.edit().putBoolean(AppConstants.PREF_MIGRATION_GEOHASH_DONE, true).apply()
    }
}
