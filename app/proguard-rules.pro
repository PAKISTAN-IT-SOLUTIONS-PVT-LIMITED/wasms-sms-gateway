# WaSMS SMS Gateway ProGuard Rules

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class net.wasms.smsgateway.**$$serializer { *; }
-keepclassmembers class net.wasms.smsgateway.** { *** Companion; }
-keepclasseswithmembers class net.wasms.smsgateway.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Room entities
-keep class net.wasms.smsgateway.data.local.model.** { *; }

# Keep API models
-keep class net.wasms.smsgateway.data.remote.model.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
