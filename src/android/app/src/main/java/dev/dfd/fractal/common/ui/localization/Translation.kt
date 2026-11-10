package dev.dfd.fractal.common.ui.localization

import android.content.Context
import android.os.LocaleList
import android.util.Log
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.layoutDirection
import dev.dfd.fractal.nativeinterface.NativeLocalization
import java.io.IOException
import java.text.MessageFormat
import java.util.Locale

private const val TRANSLATIONS_FOLDER = "translations"

/**
 * Translation files of a language, loaded in order: entries of a later file replace the ones of
 * an earlier file. cemu.po holds the Cemu core strings and is generated from upstream Cemu's
 * catalogs at build time; fractal.po holds the app's own strings and is tracked in the repository.
 */
private val TRANSLATION_FILES = listOf("cemu.po", "fractal.po")

const val DEFAULT_LANGUAGE = "en"

data class Language(val code: String, val displayName: String, val locale: Locale)

/** Languages offered by the app. English is the source language (the msgids), so it needs no file. */
private val SupportedLanguages = listOf(
    Language("en", "English", Locale.forLanguageTag("en")),
    Language("pt", "Português (Brasil)", Locale.forLanguageTag("pt-BR")),
    Language("es", "Español", Locale.forLanguageTag("es")),
)

fun getAvailableLanguages(): List<Language> = SupportedLanguages

fun isSupportedLanguage(code: String?): Boolean = SupportedLanguages.any { it.code == code }

/**
 * Language picked on the first run: the device's language when the app has it, English otherwise.
 * Only the primary device language is considered.
 */
fun deviceLanguage(): String {
    val locale = LocaleList.getDefault().get(0) ?: Locale.getDefault()
    return when (locale.language) {
        "pt" -> "pt"
        "es" -> "es"
        else -> DEFAULT_LANGUAGE
    }
}

@Volatile
private var Strings: Map<String, String> = emptyMap()

private fun loadStrings(context: Context, languageCode: String): Map<String, String> {
    val strings = mutableMapOf<String, String>()
    for (file in TRANSLATION_FILES) {
        val assetFile = "$TRANSLATIONS_FOLDER/$languageCode/$file"
        try {
            context.assets.open(assetFile).use { strings.putAll(parsePoFile(it)) }
        } catch (_: IOException) {
            Log.w("FractalTranslations", "missing translation asset: $assetFile")
        }
    }
    return strings
}

/** Applies [languageCode] to the app and the Cemu core; unsupported codes fall back to English. */
fun setLanguage(languageCode: String, context: Context) {
    val language = SupportedLanguages.firstOrNull { it.code == languageCode }
        ?: SupportedLanguages.first { it.code == DEFAULT_LANGUAGE }
    val strings = if (language.code == DEFAULT_LANGUAGE) emptyMap() else loadStrings(context, language.code)
    Strings = strings
    NativeLocalization.setTranslations(strings)
    CurrentLanguage = language
}

fun trNoop(text: String) = text

fun tr(text: String): String = Strings[text] ?: text

fun tr(text: String, vararg args: Any): String = MessageFormat.format(tr(text), *args)

private var CurrentLanguage by mutableStateOf(SupportedLanguages.first())

/** Code of the language in use; observable from composition. */
val currentLanguageCode: String get() = CurrentLanguage.code

val LocalLocale = staticCompositionLocalOf { Locale.forLanguageTag(DEFAULT_LANGUAGE) }

@Composable
fun TranslatableContent(content: @Composable () -> Unit) {
    val currentLocale = CurrentLanguage.locale
    val layoutDirection = when (currentLocale.layoutDirection) {
        LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalLocale provides currentLocale,
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}
