-keep class com.sun.jna.** { *; }
-keep class com.rovia.music.uniffi.** { *; }
-dontwarn java.awt.**

# Generated kotlinx.serialization serializers are looked up reflectively
# (the navigation destinations), so they must survive shrinking.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class *$$serializer { *; }
