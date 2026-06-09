# Release build keeps minify off (internal sideloaded app), but these rules make it safe to turn on.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.nursery.scanner.data.remote.** {
    *** Companion;
}
-keep class com.nursery.scanner.data.remote.**$$serializer { *; }
-keepclasseswithmembers class com.nursery.scanner.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
