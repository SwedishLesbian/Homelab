# Add project specific ProGuard rules here.
-keep class com.homelab.app.data.remote.dto.** { *; }
-keep class net.schmizz.** { *; }
-keep class net.i2p.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn net.i2p.crypto.**
-dontwarn javax.naming.**
-dontwarn org.slf4j.**
# sshj GSSAPI/Kerberos auth - not used on Android, classes don't exist in Android SDK
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
