package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.feature.chat.ui.ReactionChip
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.ReactionOption
import dev.kyralabs.pingogram.feature.chat.ui.ReactionPicker
import dev.kyralabs.pingogram.feature.chat.ui.ReactionSection
import dev.kyralabs.pingogram.feature.chat.ui.ReactionSectionKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The reactions of the actions sheets in [ChatPreviewData]: their quick rows and reactions pickers. */
internal object ReactionPreviewData {
    /** The quick reactions, TDLib's top ones. */
    val topReactions = listOf("👍", "❤️", "🔥", "🎉", "😁", "😢", "🤔", "👏")

    /** [emoji] as the actions sheets offer them, the user's [chosen] one checked. */
    fun reactionOptions(
        emoji: List<String>,
        chosen: String? = null,
    ): ImmutableList<ReactionOption> =
        emoji.map { ReactionOption(ReactionLabel.Emoji(it), isChosen = it == chosen) }.toImmutableList()

    /** The reactions picker of a message with [onMessage] reactions, the user's [chosen] one checked. */
    fun reactionPicker(
        onMessage: ImmutableList<ReactionChip>,
        chosen: String?,
    ) = ReactionPicker(
        onMessage = onMessage,
        sections =
            persistentListOf(
                ReactionSection(ReactionSectionKind.Top, reactionOptions(topReactions, chosen)),
                ReactionSection(
                    ReactionSectionKind.Recent,
                    (
                        reactionOptions(listOf("😍", "🙏", "👌")) +
                            ReactionOption(ReactionLabel.CustomEmoji(RECENT_CUSTOM_EMOJI), isChosen = false)
                    ).toImmutableList(),
                ),
                ReactionSection(
                    ReactionSectionKind.Popular,
                    reactionOptions(
                        listOf(
                            "🥰",
                            "😱",
                            "🤯",
                            "😎",
                            "🤩",
                            "🥳",
                            "😇",
                            "🤗",
                            "🙈",
                            "🤝",
                            "✍️",
                            "💯",
                            "⚡",
                            "🏆",
                            "💔",
                            "🤨",
                            "😐",
                            "🍓",
                            "🍾",
                            "💋",
                            "🖕",
                            "😈",
                            "😴",
                            "😭",
                        ),
                    ),
                ),
            ),
    )

    /** A custom emoji the user reacted with recently, without a picture. */
    private const val RECENT_CUSTOM_EMOJI = 7L
}
