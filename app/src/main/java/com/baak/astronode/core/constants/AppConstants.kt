package com.baak.astronode.core.constants

object AppConstants {
    // SQM seri port ayarları
    const val SQM_BAUD_RATE = 115200
    const val SQM_DATA_BITS = 8
    const val SQM_STOP_BITS = 1
    const val SQM_READ_TIMEOUT_MS = 3000L
    const val SQM_READ_COMMAND = "rx\r"

    // MPSAS geçerli aralık
    const val MPSAS_MIN = 10.0
    const val MPSAS_MAX = 25.0

    // Konum güncelleme
    const val LOCATION_INTERVAL_MS = 5000L
    const val LOCATION_MIN_DISTANCE_M = 10f

    // Oryantasyon filtresi
    const val ORIENTATION_LOW_PASS_ALPHA = 0.15f

    // Firestore
    const val FIRESTORE_CACHE_SIZE_BYTES = 50L * 1024 * 1024  // 50MB
    const val FIRESTORE_COLLECTION_MEASUREMENTS = "measurements"
    const val FIRESTORE_COLLECTION_SESSIONS = "sessions"
    const val FIRESTORE_QUERY_LIMIT = 500L

    // Haptic feedback
    const val VIBRATION_DURATION_MS = 200L

    // Migration
    const val PREF_MIGRATION_GEOHASH_DONE = "migration_geohash_done"
}
