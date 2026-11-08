package dev.dfd.fractal.nativeinterface

object NativeLogging {
    @JvmStatic
    external fun log(message: String?)

    @JvmStatic
    external fun crashLog(stacktrace: String?)
}
