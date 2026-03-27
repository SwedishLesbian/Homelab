# Add project specific ProGuard rules here.
-keep class com.homelab.app.data.remote.dto.** { *; }
-keep class net.schmizz.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
