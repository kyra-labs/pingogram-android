package dev.kyralabs.pingogram.core.domain.model

/**
 * Whether this text mentions user [userId]: by name (td_api.tl `textEntityTypeMentionName`), or by one of their
 * [usernames] (`textEntityTypeMention`, "@ada", in any letter case). Unlike TDLib's unread mention, it holds once the
 * message is read.
 */
fun FormattedText.mentionsUser(
    userId: Long?,
    usernames: Collection<String>,
): Boolean =
    userId != null &&
        entities.any { entity ->
            when (val type = entity.type) {
                is TextEntityType.MentionName -> {
                    type.userId == userId
                }

                TextEntityType.Mention -> {
                    val name = mentionedUsername(entity)
                    name != null && usernames.any { it.equals(name, ignoreCase = true) }
                }

                else -> {
                    false
                }
            }
        }

/** The username [entity] mentions, without its "@"; null where the entity lies outside the text. */
private fun FormattedText.mentionedUsername(entity: TextEntity): String? {
    val end = entity.offset.toLong() + entity.length
    if (entity.offset < 0 || end > text.length) return null
    return text.substring(entity.offset, end.toInt()).removePrefix("@")
}
