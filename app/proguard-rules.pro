# Compose, CameraX, Coil and kotlinx-coroutines all ship their own consumer rules, so most of
# the work is done for us. These cover the parts R8 cannot see through.

# Kotlin coroutines internals reached reflectively by the debug agent / service loader.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# CameraX loads its camera implementation through a provider named in the manifest metadata,
# so the entry points must survive by name.
-keep class androidx.camera.camera2.Camera2Config { *; }
-keep class androidx.camera.camera2.internal.** { *; }
-keep class * implements androidx.camera.core.CameraXConfig$Provider { *; }

# The app's own data classes are plain Kotlin and hold no reflective contract, so they are
# free to be renamed. Nothing here is serialized, parsed from JSON, or looked up by name.
