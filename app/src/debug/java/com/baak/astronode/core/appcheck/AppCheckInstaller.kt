package com.baak.astronode.core.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug derlemelerde App Check debug sağlayıcısı kullanılır.
 * İlk çalıştırmada Logcat'te "DebugAppCheckProvider" etiketiyle bir token yazılır;
 * bu token Firebase Console → App Check → Uygulamalar → Debug token'ları yönet bölümüne eklenmelidir.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
