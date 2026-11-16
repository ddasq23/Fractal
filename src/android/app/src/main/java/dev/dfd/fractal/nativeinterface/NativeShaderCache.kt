package dev.dfd.fractal.nativeinterface

/** Cemu FileCache files (transferable shaders and Vulkan pipelines) for the community shader caches. */
object NativeShaderCache {
    /** {entries, extraVersion}, or null when [path] is not a valid cache. */
    @JvmStatic
    external fun info(path: String): LongArray?

    /**
     * Adds the entries of [sourcePath] that [targetPath] doesn't have; the target is created when missing.
     * Returns {added, entries after}; added is -1 for an invalid source, -2 for a cache of another version,
     * -3 when the target can't be written.
     */
    @JvmStatic
    external fun merge(sourcePath: String, targetPath: String): LongArray
}
