# AstroNode R8 kuralları

# Crashlytics: okunabilir stack trace için satır numaraları
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep public class * extends java.lang.Exception

# usb-serial-for-android: sürücüler yansıma ile bulunuyor
-keep class com.hoho.android.usbserial.driver.** { *; }

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Firestore belge eşlemesi Map ile yapılıyor (POJO yok); yine de model sınıflarını koru
-keep class com.baak.astronode.core.model.** { *; }
