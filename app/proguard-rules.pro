# =============================================================================
# CampusApp ProGuard / R8 混淆规则
# =============================================================================
# 生产环境必须启用 minifyEnabled，以避免：
# 1. APK 被轻易反编译
# 2. API 地址、Token、高德 Key 等敏感信息暴露
# 3. 业务逻辑被逆向分析
# =============================================================================

# ── 保留行号信息，便于崩溃日志分析 ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Hilt / Dagger ──
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends androidx.hilt.work.** { *; }

# ── Retrofit / OkHttp / Okio ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ── Gson ──
-keepattributes SerializedName
-keep class com.example.campus.data.remote.dto.** { *; }
-keep class com.example.campus.data.local.entity.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# ── Glide ──
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── 高德地图 SDK ──
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**

# ── ViewModel / LiveData / Lifecycle ──
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── DataBinding / ViewBinding ──
-keep class * implements androidx.viewbinding.ViewBinding { *; }
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static ** bind(android.view.View);
    public static ** inflate(android.view.LayoutInflater);
}

# ── Navigation Component ──
-keep class * extends androidx.navigation.Navigator { *; }

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Hilt ViewModel Factory ──
-keep class dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# ── CampusApp 核心类 ──
-keep class com.example.campus.core.di.** { *; }
-keep class com.example.campus.core.common.** { *; }

# ── Crash 日志保留 ──
-keep class * extends java.lang.Exception { *; }

# ── Parcelable ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── Serializable ──
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Enum ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── R 文件（资源引用） ──
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ── WebView ──
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
}
