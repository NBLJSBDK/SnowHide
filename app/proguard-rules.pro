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
# ⚠️ Shizuku UserService（shell 身份命令执行服务）：
# 类由 Shizuku server 端实例化（构造器/onTransact 通过反射框架调用），
# R8 混淆会破坏构造器导致 release 版所有 pm 命令挂起（真机实锤：
# 冻结解冻无效、移出卡死在解冻步骤）。必须整体 keep。
-keep class com.nbljsbdk.snowhide.core.engine.impl.ShellCommandService { *; }
