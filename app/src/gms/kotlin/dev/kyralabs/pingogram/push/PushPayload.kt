package dev.kyralabs.pingogram.push

/**
 * The payload `processPushNotification` takes: "JSON-encoded push notification payload with all fields sent by the
 * server, and `google.sent_time` and `google.notification.sound` fields added" (td_api.tl). Firebase hands the fields
 * of a data message over as strings ([data]), [sentTime] in milliseconds, and the sound only for a message with a
 * notification part, which Telegram does not send to apps that encrypt their pushes.
 *
 * Plain Kotlin, so it is tested on the JVM without Play services.
 */
internal object PushPayload {
    fun json(
        data: Map<String, String>,
        sentTime: Long,
        notificationSound: String?,
    ): String =
        buildString {
            append('{')
            data.forEach { (key, value) ->
                appendString(key)
                append(':')
                appendString(value)
                append(',')
            }
            appendString(SENT_TIME)
            append(':')
            append(sentTime)
            if (notificationSound != null) {
                append(',')
                appendString(NOTIFICATION_SOUND)
                append(':')
                appendString(notificationSound)
            }
            append('}')
        }

    /** [value] as a JSON string (RFC 8259): quotes, backslashes and control characters escaped. */
    private fun StringBuilder.appendString(value: String) {
        append('"')
        value.forEach { char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' -> append("\\u").append(char.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))
                else -> append(char)
            }
        }
        append('"')
    }

    private const val SENT_TIME = "google.sent_time"
    private const val NOTIFICATION_SOUND = "google.notification.sound"
    private const val HEX = 16
    private const val UNICODE_ESCAPE_DIGITS = 4
}
