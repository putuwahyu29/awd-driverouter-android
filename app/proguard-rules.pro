# Project specific ProGuard rules

# Standard Android rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Hilt
-keep,allowobfuscation,allowshrinking @dagger.hilt.EntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.InstallIn class *

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Microsoft Graph / MSAL
-keep class com.microsoft.identity.client.** { *; }
-dontwarn com.microsoft.identity.client.**
-keep class com.microsoft.graph.** { *; }
-dontwarn com.microsoft.graph.**
-dontwarn com.microsoft.device.display.DisplayMask

# Google Drive / API Client
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.auto.value.AutoValue

# XmlPull Conflict Fix
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
-keep interface org.xmlpull.v1.** { *; }

# Apache Http (transitive)
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Reactor (transitive)
-dontwarn reactor.blockhound.**
-dontwarn io.opentelemetry.**

# SSHJ / Sardine (WebDAV)
-dontwarn com.hierynomus.sshj.**
-dontwarn com.github.sardine.**

# Maintain Compose classes
-keepclassmembers class * extends androidx.compose.ui.node.Owner {
   <fields>;
   <methods>;
}
