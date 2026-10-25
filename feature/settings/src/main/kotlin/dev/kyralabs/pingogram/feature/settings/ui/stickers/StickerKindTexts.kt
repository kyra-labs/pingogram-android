package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.feature.settings.R

/**
 * The words Settings → Stickers' pages and a set's sheet use for a [StickerSetKind]: "Trending stickers" or "Trending
 * emoji", "24 stickers" or "24 emoji", and the like. What does not depend on the kind keeps its own string.
 */
@Suppress("LongParameterList") // One per word that differs by kind: a table of strings, not behaviour.
internal class StickerKindTexts(
    @StringRes val page: Int,
    @StringRes val loading: Int,
    @StringRes val trending: Int,
    @StringRes val archived: Int,
    @StringRes val sectionYours: Int,
    @StringRes val none: Int,
    @StringRes val footer: Int,
    @StringRes val show: Int,
    @StringRes val removeText: Int,
    @StringRes val addedDone: Int,
    @StringRes val trendingFooter: Int,
    @StringRes val archivedFooter: Int,
    @StringRes val trendingEmptyBody: Int,
    @StringRes val archivedEmptyBody: Int,
    @StringRes val sheetAdd: Int,
    @StringRes val sheetAdding: Int,
    @StringRes val sheetRemoving: Int,
    @PluralsRes val count: Int,
    /** The path of a set's link: `t.me/<linkPath>/<name>`. */
    val linkPath: String,
)

private val StickerTexts =
    StickerKindTexts(
        page = R.string.settings_stickers,
        loading = R.string.settings_stickers_loading,
        trending = R.string.settings_stickers_trending,
        archived = R.string.settings_stickers_archived,
        sectionYours = R.string.settings_stickers_section_yours,
        none = R.string.settings_stickers_none,
        footer = R.string.settings_stickers_footer,
        show = R.string.settings_stickers_show,
        removeText = R.string.settings_stickers_remove_text,
        addedDone = R.string.settings_stickers_added_done,
        trendingFooter = R.string.settings_stickers_trending_footer,
        archivedFooter = R.string.settings_stickers_archived_footer,
        trendingEmptyBody = R.string.settings_stickers_trending_empty_body,
        archivedEmptyBody = R.string.settings_stickers_archived_empty_body,
        sheetAdd = R.string.sticker_set_add,
        sheetAdding = R.string.sticker_set_adding,
        sheetRemoving = R.string.sticker_set_removing,
        count = R.plurals.settings_stickers_sticker_count,
        linkPath = "addstickers",
    )

private val EmojiTexts =
    StickerKindTexts(
        page = R.string.settings_emoji,
        loading = R.string.settings_emoji_loading,
        trending = R.string.settings_emoji_trending,
        archived = R.string.settings_emoji_archived,
        sectionYours = R.string.settings_emoji_section_yours,
        none = R.string.settings_emoji_none,
        footer = R.string.settings_emoji_footer,
        show = R.string.settings_emoji_show,
        removeText = R.string.settings_emoji_remove_text,
        addedDone = R.string.settings_emoji_added_done,
        trendingFooter = R.string.settings_emoji_trending_footer,
        archivedFooter = R.string.settings_emoji_archived_footer,
        trendingEmptyBody = R.string.settings_emoji_trending_empty_body,
        archivedEmptyBody = R.string.settings_emoji_archived_empty_body,
        sheetAdd = R.string.emoji_set_add,
        sheetAdding = R.string.emoji_set_adding,
        sheetRemoving = R.string.emoji_set_removing,
        count = R.plurals.settings_emoji_count,
        linkPath = "addemoji",
    )

/**
 * The pages the list opens, worded by [texts]: the trending (false) or archived sets (true) through [onOpenList], and
 * the Emoji page through [onOpenEmoji] (null on the Emoji page itself).
 */
internal class StickerPages(
    val texts: StickerKindTexts,
    val onOpenList: (archived: Boolean) -> Unit,
    val onOpenEmoji: (() -> Unit)?,
)

/** The words for sets of [kind]. */
internal fun stickerKindTexts(kind: StickerSetKind): StickerKindTexts =
    when (kind) {
        StickerSetKind.Stickers -> StickerTexts
        StickerSetKind.Emoji -> EmojiTexts
    }

/** [StickerSetKind.Emoji] where [emoji], as the pages' routes say it. */
internal fun stickerSetKind(emoji: Boolean): StickerSetKind =
    if (emoji) StickerSetKind.Emoji else StickerSetKind.Stickers
