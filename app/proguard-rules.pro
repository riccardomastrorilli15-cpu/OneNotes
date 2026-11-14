# ---------------------------------------------------------------------------
# OneNotes - release rules
# ---------------------------------------------------------------------------

# Room: keep the generated database implementations and the schema.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
-keep class com.onenotes.app.data.db.** { *; }

# Kotlin coroutines.
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# OkHttp / Drive REST client.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepattributes Signature
-keepattributes *Annotation*

# ML Kit text recognition.
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.vision.text.** { *; }

# Samsung / sesl: the One UI libraries rely on reflection for a few
# internal widgets, keep their public API intact.
-keep class dev.oneuiproject.oneui.** { *; }
-dontwarn androidx.appcompat.**
-dontwarn com.google.android.material.**

# Samsung's "sesl" additions are looked up reflectively by the framework.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class **.R$* { public static <fields>; }
