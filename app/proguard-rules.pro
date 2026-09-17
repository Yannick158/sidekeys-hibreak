# Keep the accessibility service (referenced from the manifest)
-keep class com.sidekeys.hibreak.service.** { *; }

# Saved mappings and settings are JSON on disk. Every read is wrapped in
# runCatching with a default, so a serializer R8 stripped away would not crash
# -- it would silently reset the user's mappings. Keep the generated serializers
# and their Companions, and keep the field names, because they ARE the JSON keys.
-keepattributes *Annotation*, InnerClasses
-keep class com.sidekeys.hibreak.core.model.**$$serializer { *; }
-keepclassmembers class com.sidekeys.hibreak.core.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
# Enum constants are written to JSON by name.
-keepclassmembers enum com.sidekeys.hibreak.core.model.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Shizuku.newProcess is a hidden method reached by reflection in ShizukuShell.
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

# Line numbers in a crash report are worth far more than the few bytes they
# cost, and the source file name itself carries nothing.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
