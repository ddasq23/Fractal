-dontobfuscate

# Classes, constructors and callbacks that native code reaches through JNI (FindClass / GetMethodID).
# Without this R8 drops e.g. NativeException, and a native error aborts the process instead of
# reaching the UI as an exception.
-keep class dev.dfd.fractal.nativeinterface.** { *; }
-keepclassmembers class * implements dev.dfd.fractal.nativeinterface.** { *; }
