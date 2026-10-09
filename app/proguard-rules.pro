# ---- Kompresi maksimal ----
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-overloadaggressively
-renamesourcefileattribute SourceFile
-dontwarn **

# Buang log & pengecekan yang tidak perlu di release
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkNotNullParameter(...);
    static void checkNotNullExpressionValue(...);
    static void checkParameterIsNotNull(...);
    static void checkExpressionValueIsNotNull(...);
}

# ---- Wajib dipertahankan ----
# ShellService dibuat oleh Shizuku lewat nama kelas (reflection)
-keep class com.rahmatsobrian.miraicode.ShellService { *; }
# AIDL / Binder stub
-keep class com.rahmatsobrian.miraicode.IShellService { *; }
-keep class com.rahmatsobrian.miraicode.IShellService$Stub { *; }
-keep class com.rahmatsobrian.miraicode.IShellService$Stub$Proxy { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class rikka.hidden.compat.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }
