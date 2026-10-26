# Aangan has no libraries and uses no reflection: the manifest's activities and services are kept
# by the Android Gradle plugin, and enums are looked up by name(), which shrinking doesn't change.
# Keep line numbers so stack traces in bug reports can be read against the source.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
