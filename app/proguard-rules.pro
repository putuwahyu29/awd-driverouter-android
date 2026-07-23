# Project specific ProGuard rules

# Standard Android rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Box SDK
-keep class com.box.sdk.** { *; }
-dontwarn com.box.sdk.**
-keep class com.eclipsesource.json.** { *; }
-dontwarn com.eclipsesource.json.**
-keepattributes *Annotation*

# Microsoft Authentication Library (MSAL) & Graph
-keep class com.microsoft.identity.client.** { *; }
-dontwarn com.microsoft.identity.client.**
-keep class com.microsoft.graph.** { *; }
-dontwarn com.microsoft.graph.**
-dontwarn com.microsoft.device.display.DisplayMask
-keep class com.microsoft.identity.common.** { *; }
-keep interface com.microsoft.identity.client.** { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault

# Dropbox SDK
-keep class com.dropbox.core.** { *; }
-dontwarn com.dropbox.core.**

# Google Drive / API Client
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.services.drive.**
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.auto.value.AutoValue

# Model Data (Retrofit/Gson mapping)
-keep class com.awd.driverouter.data.remote.** { *; }
-keep class com.awd.driverouter.domain.model.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# Preserve generic signatures of Retrofit interfaces for suspend functions
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# Keep the update service and its data model
-keep class com.awd.driverouter.data.remote.GitHubUpdateService { *; }
-keep class com.awd.driverouter.data.remote.GitHubRelease { *; }

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
-keepclassmembers class ** {
  @androidx.room.* *;
}

# Gson
-keep class com.google.gson.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# XmlPull Conflict Fix
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
-keep interface org.xmlpull.v1.** { *; }

# Apache Http (transitive)
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn javax.security.auth.**
-dontwarn org.ietf.jgss.**

# Guava / WorkManager ListenableFuture
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-dontwarn com.google.common.util.concurrent.ListenableFuture

# Reactor (transitive)
-dontwarn reactor.blockhound.**
-dontwarn io.opentelemetry.**

# SSHJ / Sardine (WebDAV)
-dontwarn com.hierynomus.sshj.**
-dontwarn com.github.sardine.**
-keep class com.github.sardine.** { *; }
-keep class com.hierynomus.sshj.** { *; }
-keep class net.schmizz.sshj.** { *; }

# WorkManager & Hilt Workers
-keep class androidx.work.WorkerParameters { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class com.awd.driverouter.data.worker.** { *; }

# Maintain Providers (for Dagger Multibinding and Reflection)
-keep class com.awd.driverouter.data.provider.** { *; }
-keep interface com.awd.driverouter.domain.provider.CloudProvider { *; }

# Maintain Compose classes
-keepclassmembers class * extends androidx.compose.ui.node.Owner {
   <fields>;
   <methods>;
}

# Hilt
-keep,allowobfuscation,allowshrinking @dagger.hilt.EntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.InstallIn class *
