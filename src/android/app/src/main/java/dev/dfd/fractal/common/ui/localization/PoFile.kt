package dev.dfd.fractal.common.ui.localization

import java.io.InputStream

private enum class PoKey { MSG_ID, MSG_STR, NONE }

/**
 * Reads the msgid -> msgstr pairs of a gettext .po file. Untranslated (empty msgstr), fuzzy,
 * plural and context entries are skipped: neither the app nor the core code uses them.
 */
fun parsePoFile(source: InputStream): Map<String, String> {
    val reader = source.bufferedReader()
    var key = PoKey.NONE
    var msgId = StringBuilder()
    var msg = StringBuilder()
    var skip = false
    var fuzzy = false
    var hasMsgStr = false
    val strings = mutableMapOf<String, String>()

    fun flush() {
        if (hasMsgStr && !skip && !fuzzy && msgId.isNotEmpty() && msg.isNotEmpty()) {
            strings[msgId.toString()] = msg.toString()
        }
        key = PoKey.NONE
        msgId = StringBuilder()
        msg = StringBuilder()
        skip = false
        fuzzy = false
        hasMsgStr = false
    }

    while (true) {
        val line = reader.readLine()?.trim() ?: break

        when {
            line.isEmpty() -> flush()

            line.startsWith("#") -> {
                if (hasMsgStr) flush()
                if (line.startsWith("#,") && line.contains("fuzzy")) fuzzy = true
            }

            line.startsWith("msgctxt ") || line.startsWith("msgid_plural ") -> {
                if (line.startsWith("msgctxt ") && hasMsgStr) flush()
                skip = true
                key = PoKey.NONE
            }

            line.startsWith("msgid ") -> {
                if (hasMsgStr) {
                    val wasFuzzy = fuzzy
                    flush()
                    fuzzy = wasFuzzy
                }
                key = PoKey.MSG_ID
                msgId.append(line.substringAfter("msgid ").unquote())
            }

            line.startsWith("msgstr ") -> {
                key = PoKey.MSG_STR
                hasMsgStr = true
                msg.append(line.substringAfter("msgstr ").unquote())
            }

            line.startsWith("msgstr[") -> {
                skip = true
                hasMsgStr = true
                key = PoKey.NONE
            }

            line.startsWith("\"") -> when (key) {
                PoKey.MSG_ID -> msgId.append(line.unquote())
                PoKey.MSG_STR -> msg.append(line.unquote())
                PoKey.NONE -> {}
            }
        }
    }
    flush()

    return strings
}

private fun String.unquote(): String {
    val s = trim().removePrefix("\"").removeSuffix("\"")
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (val n = s[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                else -> out.append('\\').append(n)
            }
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
