package dev.dfd.fractal.nativeinterface

object NativeLocalization {
    @JvmStatic
    external fun setTranslations(translations: Map<String, String>)
}
