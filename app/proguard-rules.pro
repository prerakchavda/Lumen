# ---- R8 rules for Lumen ----------------------------------------------------------------------
# Most libraries (kotlinx.serialization, Retrofit, OkHttp, Media3, Hilt, Coil, WorkManager)
# ship their own consumer rules. The ones below cover libraries that don't.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-dontnote kotlinx.serialization.SerializationKt

# NewPipeExtractor (YouTube trailer resolution): loads localization/time-ago pattern classes
# and services reflectively, and runs YouTube's player JS through Rhino.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }

# Rhino JavaScript engine (used by NewPipe to decipher stream URLs): heavy reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# jsoup optional dependency
-dontwarn com.google.re2j.**

# Our own @Serializable DTOs are covered by kotlinx.serialization's bundled rules; keep enum
# names stable too since some are persisted as strings in DataStore.
-keepclassmembers enum com.seerflix.tv.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Media3 FFmpeg audio decoder extension (loaded reflectively by DefaultRenderersFactory & FfmpegAudioSupport).
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
