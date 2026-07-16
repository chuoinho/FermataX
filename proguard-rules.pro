-keepattributes LineNumberTable,SourceFile
-keepnames class me.aap.** { *; }
-keep class me.app.fermatax.auto.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep class me.aap.fermata.vfs.sftp.** { *; }
-keep class me.aap.fermata.vfs.smb.** { *; }
-keep class me.aap.fermata.vfs.gdrive.** { *; }
-keep class androidx.car.app.** { *; }
-keep class org.chromium.net.impl.NativeCronetEngineBuilderImpl { *; }

-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.jcraft.jsch.PageantConnector
-dontwarn okio.*

-keepnames class androidx.media3.exoplayer.ExoPlayerImpl { *; }
-keepnames class androidx.media3.exoplayer.ExoPlayerImplInternal { *; }

-keep class me.aap.fermata.mlkit.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
