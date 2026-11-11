# kotlinx.serialization keeps generated serializers via its bundled rules.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
# MapLibre uses JNI callbacks into these packages.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
