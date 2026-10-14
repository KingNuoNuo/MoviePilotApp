# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jinge.moviepilot.**$$serializer { *; }
-keepclassmembers class com.jinge.moviepilot.** {
    *** Companion;
}
-keepclasseswithmembers class com.jinge.moviepilot.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil（AAPT 资源引用）
-dontwarn coil.**
