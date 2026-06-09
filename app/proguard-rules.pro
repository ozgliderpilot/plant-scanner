# Release build keeps minify off (internal sideloaded app); these rules make it safe to turn on.

# kotlinx.serialization — generic keeps that cover EVERY @Serializable class, wherever it lives
# (the DTOs in data.remote today, plus anything added later), not just one package.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
