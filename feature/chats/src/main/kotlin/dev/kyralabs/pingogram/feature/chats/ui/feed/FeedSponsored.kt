package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessage
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheet
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The channels' sponsored messages in the feed (`REQUIREMENTS.md` §2, FR-X5; `docs/DESIGN_SYSTEM.md` §6 "Channel
 * feed", frames 3 and 3b; Telegram's API terms), as the conversation's (`ChatSponsoredMessages` of `feature:chat`):
 * shown as TDLib returns them, labelled, and never changed; only a report takes one away.
 *
 * - **Loading** ([load]): each channel's once per feed session, [PARALLEL_LOADS] at a time, never polled.
 * - **Placing** ([state]): a channel's first one, after that channel's newest post; never counted as unread.
 * - **Viewed** ([FeedAdEvent.Viewed]): once its whole card is on screen, through [view], once per session.
 * - **Opening** ([FeedAdEvent.Open]): TDLib hears of the click, then the sponsor's link opens ([FeedEffect.OpenUrl]).
 * - **About this ad** ([FeedAdEvent.ShowInfo]) and reporting it ([FeedAdReports]), as the conversation's sheet does
 *   (`core:ui`'s `SponsoredMessageSheet`).
 */
internal class FeedSponsored(
    private val actions: FeedCardActions,
    private val view: suspend (chatId: Long, ids: List<Long>) -> Outcome<Unit>,
    private val scope: CoroutineScope,
    private val onEffect: (FeedEffect) -> Unit,
) {
    /** Each loaded channel's sponsored messages, by channel. */
    private val loaded = MutableStateFlow<Map<Long, List<SponsoredMessage>>>(emptyMap())

    /** Reported here, or all of them once Telegram hid ads for the user. */
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())

    private val asked = mutableSetOf<Long>()
    private val viewed = mutableSetOf<Long>()
    private val loads = Semaphore(PARALLEL_LOADS)

    private val reports =
        FeedAdReports(
            actions = actions,
            scope = scope,
            onEffect = onEffect,
            onHide = { id -> hidden.update { it + id } },
            onHideAll = {
                hidden.update {
                    it +
                        loaded.value.values
                            .flatten()
                            .map(SponsoredMessage::id)
                }
            },
        )

    /** Each channel's shown ad, by channel, and "About this ad" while it is open. */
    val state: Flow<FeedAds> =
        combine(loaded, hidden, reports.sheet) { loaded, hidden, sheet ->
            val ads =
                loaded.mapNotNull { (chatId, ads) ->
                    ads.firstOrNull { it.id !in hidden }?.let { chatId to it.toFeedAd(chatId) }
                }
            FeedAds(ads.toMap().toImmutableMap(), sheet?.sheet)
        }

    /** Loads the sponsored messages of [chatIds] not asked for yet in this session. */
    fun load(chatIds: Collection<Long>) {
        chatIds.filter(asked::add).forEach { chatId ->
            scope.launch {
                loads.withPermit {
                    val result = actions.ads(chatId)
                    if (result is Outcome.Success) loaded.update { it + (chatId to result.value.messages) }
                }
            }
        }
    }

    fun onEvent(event: FeedAdEvent) {
        when (event) {
            is FeedAdEvent.Viewed -> {
                if (ad(event.chatId, event.id) != null && viewed.add(event.id)) {
                    scope.launch { view(event.chatId, listOf(event.id)) }
                }
            }

            is FeedAdEvent.Open -> {
                open(event)
            }

            is FeedAdEvent.ShowInfo -> {
                ad(event.chatId, event.id)?.let { reports.show(event.chatId, it) }
            }

            else -> {
                reports.onEvent(event)
            }
        }
    }

    private fun ad(
        chatId: Long,
        id: Long,
    ): SponsoredMessage? = loaded.value[chatId]?.firstOrNull { it.id == id }

    private fun open(event: FeedAdEvent.Open) {
        val ad = ad(event.chatId, event.id) ?: return
        // Sent before this returns: opening the link leaves the app.
        scope.launch(
            start = CoroutineStart.UNDISPATCHED,
        ) { actions.clickAd(event.chatId, event.id, event.isMediaClick) }
        ad.sponsor.url
            .takeIf(String::isNotBlank)
            ?.let { onEffect(FeedEffect.OpenUrl(it)) }
    }

    private companion object {
        const val PARALLEL_LOADS = 4
    }
}

/** The ads the feed shows, by channel, and "About this ad" while it is open. */
internal data class FeedAds(
    val byChannel: ImmutableMap<Long, FeedAd>,
    val sheet: SponsoredSheet?,
)

/** [this] sponsored message of channel [chatId] as its card shows it, exactly as TDLib returned it. */
private fun SponsoredMessage.toFeedAd(chatId: Long): FeedAd {
    val text =
        when (val content = content) {
            is MessageContent.Text -> content.text
            is MessageContent.Photo -> content.caption
            is MessageContent.Video -> content.caption
            is MessageContent.Animation -> content.caption
            else -> FormattedText.Empty
        }
    return FeedAd(
        chatId = chatId,
        id = id,
        isRecommended = isRecommended,
        title = title,
        text = text,
        media = feedMedia(content),
        sponsorPhoto = sponsor.photo,
        buttonText = buttonText,
    )
}
