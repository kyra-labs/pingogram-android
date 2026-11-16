package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.DiffChange
import dev.kyralabs.pingogram.core.domain.model.DiffKind
import dev.kyralabs.pingogram.core.domain.model.DiffText
import dev.kyralabs.pingogram.core.domain.model.FixedText
import dev.kyralabs.pingogram.core.domain.model.TextCompositionExample
import dev.kyralabs.pingogram.core.domain.model.TextCompositionStyle
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** The domain's style of Telegram's AI editor. */
internal fun TdApi.TextCompositionStyle.toTextCompositionStyle(): TextCompositionStyle =
    TextCompositionStyle(
        name = name,
        title = title,
        customEmojiId = customEmojiId,
        isCustom = isCustom,
        isCreator = isCreator,
        installCount = installCount,
        creatorUserId = creatorUserId,
        example = englishExample?.toExample(),
    )

internal fun TdApi.TextCompositionStyleExample.toExample(): TextCompositionExample =
    TextCompositionExample(sourceText.toFormattedText(), resultText.toFormattedText())

/** A fixed text, with its changes; a change of a kind a newer TDLib added is left unmarked. */
internal fun TdApi.FixedText.toFixedText(): FixedText =
    FixedText(
        text = text.toFormattedText(),
        diff =
            DiffText(
                text = diffText?.text ?: text.text,
                changes =
                    diffText
                        ?.entities
                        .orEmpty()
                        .mapNotNull { entity ->
                            val kind =
                                when (val type = entity.type) {
                                    is TdApi.DiffEntityTypeInsert -> DiffKind.Insert
                                    is TdApi.DiffEntityTypeDelete -> DiffKind.Delete
                                    is TdApi.DiffEntityTypeReplace -> DiffKind.Replace(type.oldText)
                                    else -> null
                                }
                            kind?.let { DiffChange(entity.offset, entity.length, it) }
                        }.toImmutableList(),
            ),
    )
