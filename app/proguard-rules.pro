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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Preserve stack traces in crash reports
-keepattributes SourceFile,LineNumberTable

# Timber — keep the debug tree for crash reporting
-keep class timber.log.** { *; }

# LiteRT-LM native bindings
-keep class com.google.ai.edge.litertlm.** { *; }

# ML Kit text recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }

# pdfbox-android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**