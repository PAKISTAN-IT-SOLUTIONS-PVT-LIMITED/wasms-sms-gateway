# WaSMS SMS Gateway ProGuard Rules

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class net.wasms.smsgateway.**$$serializer { *; }
-keepclassmembers class net.wasms.smsgateway.** { *** Companion; }
-keepclasseswithmembers class net.wasms.smsgateway.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep all @Serializable data classes
-keepclasseswithmembers class net.wasms.smsgateway.data.remote.model.** { *; }
-keepclasseswithmembers class net.wasms.smsgateway.domain.model.** { *; }

# Keep Room entities
-keep class net.wasms.smsgateway.data.local.model.** { *; }
-keep class net.wasms.smsgateway.data.local.db.** { *; }

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
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class net.zetetic.database.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Tink (used by EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Compose
-dontwarn androidx.compose.**

# WorkManager + Hilt Workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep crash report activity (plain, no Hilt)
-keep class net.wasms.smsgateway.presentation.CrashReportActivity { *; }
