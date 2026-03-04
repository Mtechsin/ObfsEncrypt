# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ──────────────────────────────────────────────────────────────────────────────
# Hilt - Dagger dependency injection
# ──────────────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep class * extends dagger.hilt.android.AndroidEntryPoint { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_MembersInjector { *; }
-keep class * implements dagger.hilt.android.internal.managers.HasComponent { *; }
-keep class * implements dagger.hilt.android.internal.managers.HasEntryPoint { *; }
-keep class * implements dagger.hilt.android.internal.managers.HasViewComponent { *; }
-dontwarn dagger.hilt.android.internal.managers.ComponentSupplier
-dontwarn dagger.hilt.internal.aggregatedroot.**
-dontwarn dagger.hilt.internal.componenttree.**
-dontwarn dagger.hilt.internal.processgeneratedroots.**

# ──────────────────────────────────────────────────────────────────────────────
# Kotlin Coroutines
# ──────────────────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ──────────────────────────────────────────────────────────────────────────────
# Kotlin
# ──────────────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ──────────────────────────────────────────────────────────────────────────────
# Jetpack Compose
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keep class * extends androidx.compose.runtime.Composable { *; }
-keepclassmembers class * extends androidx.compose.runtime.Composable {
    <init>(...);
}
-dontwarn androidx.compose.**

# ──────────────────────────────────────────────────────────────────────────────
# Argon2Kt - Password hashing library
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# ──────────────────────────────────────────────────────────────────────────────
# DataStore - Preferences storage
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ──────────────────────────────────────────────────────────────────────────────
# Coil - Image loading
# ──────────────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-keep class coil3.** { *; }
-dontwarn coil.**
-dontwarn coil3.**

# ──────────────────────────────────────────────────────────────────────────────
# Navigation Compose
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ──────────────────────────────────────────────────────────────────────────────
# DocumentFile - SAF file access
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.documentfile.** { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Keep encryption helper and related classes
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.obfs.encrypt.crypto.** { *; }
-keep class com.obfs.encrypt.data.** { *; }
-keep class com.obfs.encrypt.viewmodel.** { *; }
-keep class com.obfs.encrypt.services.** { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Keep generic signatures for reflection (if needed)
# ──────────────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ──────────────────────────────────────────────────────────────────────────────
# Prevent obfuscation of model classes used with serialization/parceling
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.obfs.encrypt.model.** { *; }