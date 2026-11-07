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

    // Konum güncelleme (pil tasarrufu: 30s, 50m min mesafe)
    const val LOCATION_INTERVAL_MS = 30_000L
    const val LOCATION_MIN_DISTANCE_M = 50f

    // Oryantasyon filtresi
    const val ORIENTATION_LOW_PASS_ALPHA = 0.15f

    // Firestore
    const val FIRESTORE_CACHE_SIZE_BYTES = 50L * 1024 * 1024  // 50MB
    const val FIRESTORE_COLLECTION_MEASUREMENTS = "measurements"
    const val FIRESTORE_COLLECTION_SESSIONS = "sessions"
    const val FIRESTORE_COLLECTION_USERS = "users"
    const val FIRESTORE_COLLECTION_SESSION_CODES = "session_codes"
    const val FIRESTORE_QUERY_LIMIT = 500L
    const val PUBLIC_SESSIONS_LIMIT = 100L
    const val FIRESTORE_BATCH_LIMIT = 400

    // Harita sorgusu: her geohash aralığı için en fazla bu kadar belge (maliyet koruması)
    const val MAP_QUERY_LIMIT_PER_BOUND = 250L
    const val MAP_MAX_RESULTS = 1500

    // Auth
    const val AUTH_TIMEOUT_MS = 10_000L

    // Alan uzunluk sınırları (firestore.rules ile aynı)
    const val SESSION_NAME_MAX = 80
    const val SESSION_DESCRIPTION_MAX = 500
    const val ORGANIZER_NAME_MAX = 60
    const val DISPLAY_NAME_MAX = 40
    const val NOTE_MAX = 500

    // Kod üretiminde çakışma olursa yeniden deneme
    const val SESSION_CODE_MAX_ATTEMPTS = 5
    const val SESSION_CODE_CHECK_TIMEOUT_MS = 4_000L

    // Haptic feedback
    const val VIBRATION_DURATION_MS = 200L

    // Profile setup
    const val PREFS_PROFILE = "astro_node_profile"
    const val PREF_PROFILE_SETUP_DONE = "profile_setup_done"

    // Yasal sayfalar (firebase/public — Firebase Hosting)
    const val PRIVACY_POLICY_URL = "https://astronode-50a6c.web.app/privacy"
    const val ACCOUNT_DELETION_URL = "https://astronode-50a6c.web.app/delete-account"

    // Session code (6 haneli, karıştırılmayan karakterler)
    const val SESSION_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val SESSION_CODE_LENGTH = 6
}
