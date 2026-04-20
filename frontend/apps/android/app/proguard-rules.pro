# Pantopus Android — ProGuard / R8 rules
# https://developer.android.com/build/shrink-code

# Keep all DTOs — Moshi reflects on them at runtime.
-keep class app.pantopus.android.data.api.models.** { *; }

# Moshi
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Retrofit
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn retrofit2.Platform$Java8

# OkHttp / Okio — already has baked rules but belt-and-suspenders.
-dontwarn okhttp3.**
-dontwarn okio.**

# Socket.IO client (Netty + JSON)
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-keep class com.github.nkzawa.** { *; }

# Hilt / Dagger generated
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Coroutines debug agent (only used in debug builds)
-dontwarn kotlinx.coroutines.debug.**

# Stripe
-keep class com.stripe.android.** { *; }
-dontwarn com.stripe.android.**
