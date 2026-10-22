package dev.kyralabs.pingogram.core.data.diagnostics

/**
 * Takes what could identify a person or say what they wrote out of a line of a crash or a log, before the feedback
 * report shows it (`docs/TESTING_GUIDE.md`): the values of TDLib fields that hold text or names, quoted strings, email
 * addresses, phone numbers and other long digit runs (ids), and long tokens. What is left says where and why something
 * failed, not to whom.
 */
internal object Redaction {
    /** TDLib's objects print `field = value`; these fields hold what people wrote or are called. */
    private val sensitiveField =
        Regex(
            """\b(text|caption|first_name|last_name|phone_number|username|usernames|title|description|query|bio|""" +
                """address|email_address|name|question|explanation|vcard|emoji)\s*[=:]\s*("[^"\n]*"|\S+)""",
        )
    private val quoted = Regex(""""[^"\n]*"|«[^»\n]*»|“[^”\n]*”""")
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

    /**
     * Seven digits or more with their sign, alone or grouped with spaces and dashes; never part of a decimal (TDLib's
     * timestamps).
     */
    private val number = Regex("""(?<![\w.])[+-]?\d[\d -]{5,}\d(?![\w.])""")
    private val token = Regex("""\b[A-Za-z0-9_-]{32,}\b""")

    fun redact(line: String): String =
        line
            .replace(sensitiveField) { "${it.groupValues[1]} = [redacted]" }
            .replace(quoted, "\"[text]\"")
            .replace(email, "[email]")
            .replace(token, "[token]")
            .replace(number, "[number]")
}
