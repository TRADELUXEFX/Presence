# Presence release build (R8). The app has no reflection, JavaScript bridge or serialized classes,
# so only the standard Android rules apply. Components listed in AndroidManifest.xml (activities,
# services, receivers, the widget and the file provider) are kept automatically.

# Keep readable stack traces for crash reports (file names and line numbers stay; names are shortened).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
