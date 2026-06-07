# ─── React Native core ───────────────────────────────────────────────────────
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
-dontwarn com.facebook.react.**
-dontwarn com.facebook.hermes.**
-dontwarn com.facebook.jni.**

# ─── React Native bridge & modules ───────────────────────────────────────────
-keep,allowobfuscation @interface com.facebook.proguard.annotations.DoNotStrip
-keep,allowobfuscation @interface com.facebook.proguard.annotations.KeepGettersAndSetters
-keep @com.facebook.proguard.annotations.DoNotStrip class *
-keepclassmembers class * {
    @com.facebook.proguard.annotations.DoNotStrip *;
}
-keepclassmembers @com.facebook.proguard.annotations.KeepGettersAndSetters class * {
    void set*(***);
    *** get*();
}
-keep class * extends com.facebook.react.bridge.JavaScriptModule { *; }
-keep class * extends com.facebook.react.bridge.NativeModule { *; }
-keepclassmembers,includedescriptorclasses class * extends com.facebook.react.bridge.NativeModule {
    native <methods>;
    @com.facebook.react.bridge.ReactMethod <methods>;
    public *;
}

# ─── AppLocker custom module ──────────────────────────────────────────────────
-keep class com.applocker.** { *; }

# ─── Hermes engine ────────────────────────────────────────────────────────────
-keep class com.facebook.hermes.unicode.** { *; }
-keep class com.facebook.hermes.intl.** { *; }

# ─── OkHttp (used by Metro bundler) ──────────────────────────────────────────
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# ─── Kotlin ───────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ─── Security crypto (EncryptedSharedPreferences) ────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ─── General Android ─────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# ─── Tink / ErrorProne annotations ──────────────────────────────────────────
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
