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

-keep class com.ecarx.xui.** { *; }
-keepnames class com.ecarx.xui.** { *; }

-keep class ecarx.car.** { *; }
-keepnames class ecarx.car.** { *; }

-keep class ecarx.fw.** { *; }
-keepnames class ecarx.fw.** { *; }

-keep class com.geely.** { *; }
-keepnames class com.geely.** { *; }

-keep class com.tencent.** { *; }
-keepnames class com.tencent.** { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    #noinspection ShrinkerUnresolvedReference
    kotlinx.serialization.KSerializer serializer(...);
}
