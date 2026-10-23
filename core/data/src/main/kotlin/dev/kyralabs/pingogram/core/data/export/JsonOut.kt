package dev.kyralabs.pingogram.core.data.export

/**
 * A small streaming JSON writer into [out], indented by two spaces, with strings escaped as RFC 8259 asks (quotes,
 * backslashes and control characters) and U+2028 / U+2029 too, so the output is also valid JavaScript. Everything else,
 * emoji included, is written as it is, in UTF-8 by the caller's writer. Plain Kotlin rather than `android.util
 * .JsonWriter`, so the export's writers are tested on the JVM.
 */
@Suppress("TooManyFunctions") // One function per kind of token and value.
internal class JsonOut(
    private val out: Appendable,
) {
    /** Per open object or array: whether it has an element yet. */
    private val hasElement = ArrayDeque<Boolean>()

    /** A name was written, so the next value follows it on the same line. */
    private var afterName = false

    fun beginObject(): JsonOut = open('{')

    fun endObject(): JsonOut = close('}')

    fun beginArray(): JsonOut = open('[')

    fun endArray(): JsonOut = close(']')

    fun name(name: String): JsonOut {
        element()
        string(name)
        out.append(": ")
        afterName = true
        return this
    }

    fun value(value: String): JsonOut {
        element()
        string(value)
        return this
    }

    fun value(value: Long): JsonOut {
        element()
        out.append(value.toString())
        return this
    }

    fun value(value: Boolean): JsonOut {
        element()
        out.append(if (value) "true" else "false")
        return this
    }

    /** [name] with [value], left out where [value] is null. */
    fun field(
        name: String,
        value: String?,
    ): JsonOut = if (value == null) this else name(name).value(value)

    fun field(
        name: String,
        value: Long?,
    ): JsonOut = if (value == null) this else name(name).value(value)

    fun field(
        name: String,
        value: Boolean,
    ): JsonOut = name(name).value(value)

    private fun open(bracket: Char): JsonOut {
        element()
        out.append(bracket)
        hasElement.addLast(false)
        return this
    }

    private fun close(bracket: Char): JsonOut {
        val hadElements = hasElement.removeLast()
        if (hadElements) newLine()
        out.append(bracket)
        if (hasElement.isEmpty()) out.append('\n')
        return this
    }

    /** Before a value or a name: a comma after the previous element, and its own line. */
    private fun element() {
        if (afterName) {
            afterName = false
            return
        }
        if (hasElement.isEmpty()) return
        if (hasElement.last()) out.append(',')
        hasElement[hasElement.lastIndex] = true
        newLine()
    }

    private fun newLine() {
        out.append('\n')
        repeat(hasElement.size) { out.append(INDENT) }
    }

    private fun string(value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> {
                    out.append("\\\"")
                }

                '\\' -> {
                    out.append("\\\\")
                }

                '\n' -> {
                    out.append("\\n")
                }

                '\r' -> {
                    out.append("\\r")
                }

                '\t' -> {
                    out.append("\\t")
                }

                ' ', ' ' -> {
                    out.append("\\u").append(char.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
                }

                else -> {
                    if (char < ' ') {
                        out.append("\\u").append(char.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
                    } else {
                        out.append(char)
                    }
                }
            }
        }
        out.append('"')
    }

    private companion object {
        const val INDENT = "  "
        const val HEX = 16
        const val UNICODE_DIGITS = 4
    }
}
