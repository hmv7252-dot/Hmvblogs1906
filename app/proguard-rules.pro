# =============================================================================
# SLIDECRAFT PRODUCTION R8 / PROGUARD SECURITY RULES
# =============================================================================

# Obfuscation & Source Mapping Protection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose and debug logging calls in production release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Room Database & Persistence Rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.** { *; }
-dontwarn androidx.room.paging.**

# Moshi & JSON Serialization
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Unity LevelPlay & IronSource Mediation Rules
-keep class com.unity3d.** { *; }
-dontwarn com.unity3d.**
-keep class com.ironsource.** { *; }
-dontwarn com.ironsource.**

# Android Jetpack Compose Runtime
-keep class androidx.compose.runtime.** { *; }

# Security & Anti-Cheat: Obfuscate implementations while preserving Keystore callbacks
-keepclassmembers class com.example.security.SecureStorageManager {
    private *** ensureMasterKey(...);
}
