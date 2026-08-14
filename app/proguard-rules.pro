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
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.lgh.tapclick.mybean.AppDescribe {*;}
-keep class com.lgh.tapclick.mybean.Widget {*;}
-keep class com.lgh.tapclick.mybean.Coordinate {*;}
-keep class com.lgh.tapclick.mybean.BasicContent {*;}
-keep class com.lgh.tapclick.mybean.CoordinateShare {*;}
-keep class com.lgh.tapclick.mybean.WidgetShare {*;}
-keep class com.lgh.tapclick.mybean.Regulation {*;}
-keep class com.lgh.tapclick.mybean.RegulationExport {*;}

# LSPosed/Xposed integrations hook this stable entrypoint by class and method name.
-keep class com.lgh.tapclick.myfunction.MyUtils {
    public static boolean isModuleValid();
}
