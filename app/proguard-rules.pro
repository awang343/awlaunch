# Keep our Activity/ViewModel — referenced by name in the manifest / framework.
-keep class com.awlaunch.MainActivity { *; }
-keep class com.awlaunch.LauncherViewModel { *; }

# org.json is used directly; built-in to the platform so no R8 stripping needed.
# Compose, AndroidX, kotlinx.coroutines ship their own consumer-rules. No extra keeps required.

# Keep filenames + line numbers in crash traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
