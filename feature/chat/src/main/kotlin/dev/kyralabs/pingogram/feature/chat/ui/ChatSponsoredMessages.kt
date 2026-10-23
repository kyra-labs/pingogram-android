package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessage
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sponsored messages of `ChatViewModel` (`REQUIREMENTS.md` §2; Telegram's API terms): shown where TDLib returns
 * them, labelled, and never hidden, changed or left out.
 *
 * - **Loading** ([start]): once per view model, for a channel or a chat with a bot only, once the chat is known,
 *   never polled. A failure is tried again when the screen starts again ([onStarted]), not in a loop.
 * - **Placing** ([state]), only while the history reaches the chat's newest message: with `messagesBetween` 0, the
 *   first one below every message, there to stay as new messages come above it; otherwise the first one below the
 *   newest sent message when it is placed, and the next one below the newest again once at least `messagesBetween`
 *   messages came after the last one placed, while there are more. A placed one stays below its message, so it never
 *   jumps; it is not in older history, and not where its message is not among the items.
 * - **Viewed** ([SponsoredEvent.Viewed]): once its whole card is on screen, through [views] like a message, so once per
 *   view model and only while the chat is open.
 * - **Opening** ([SponsoredEvent.Open]): TDLib hears of the click (`clickChatSponsoredMessage`), then the sponsor's
 *   link, or the link tapped in its text, opens ([ChatEffect.OpenUrl]).
 * - **About this ad** ([SponsoredEvent.ShowInfo]): who placed it and more about it, and reporting it, with the reasons
 *   TDLib asks for. A reported ad goes from the list, all of them if Telegram hid ads for the user; nothing else ever
 *   removes one. What came of the report is a snackbar ([ChatEffect.SponsoredNotice]); failures are
 *   [ChatEffect.ShowError] snackbars.
 *
 * Runs in [scope], the view model's; events come on the main thread, [state] is collected on the view model's
 * dispatcher.
 */
@Suppress("TooManyFunctions") // One function per event of a sponsored message and step of a report, and the loading.
internal class ChatSponsoredMessages(
    private val chatId: Long,
    private val repository: SponsoredMessageRepository,
    private val views: MessageViews,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
    private val log: ChatLog,
) {
    private val loaded = MutableStateFlow<LoadedAds?>(null)

    /** Reported here, or all of them once Telegram hid ads for the user. */
    private val hidden = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** Sponsored message id → the message it was placed below; only where `messagesBetween` is not 0. */
    private val placements = MutableStateFlow<PersistentMap<Long, Long>>(persistentMapOf())

    private val sheet = MutableStateFlow<SponsoredSheet?>(null)

    /** The options of the report step the sheet shows, by index. */
    private var options: List<ReportOption> = emptyList()

    private var loading: Job? = null
    private var failed = false
    private var reporting: Job? = null

    /** The sponsored messages placed in [history], newest first, and "About this ad" while it is open. */
    fun state(history: Flow<ChatHistory>): Flow<SponsoredState> =
        combine(history, loaded, hidden, sheet) { history, ads, hidden, sheet ->
            val placed = ads?.let { place(history, it, hidden) }.orEmpty()
            if (placed.isEmpty() && sheet == null) SponsoredState.None else SponsoredState(placed, sheet)
        }

    /** Loads the sponsored messages once [chat] is known, if it is a channel or a chat with a bot ([users]). */
    fun start(
        chat: Flow<Chat>,
        users: Flow<Map<Long, User>>,
    ) {
        scope.launch {
            val shows = combine(chat, users, ::showsSponsoredMessages).filterNotNull().first()
            if (shows) load()
        }
    }

    /** The screen started again: a load that failed is tried once more. */
    fun onStarted() {
        if (failed) load()
    }

    fun onEvent(event: SponsoredEvent) {
        when (event) {
            is SponsoredEvent.Viewed -> if (ad(event.messageId) != null) views.setVisible(setOf(event.messageId))
            is SponsoredEvent.Open -> open(event.messageId, event.isMediaClick, event.url)
            is SponsoredEvent.ShowInfo -> showInfo(event.messageId)
            SponsoredEvent.DismissInfo -> sheet.value = null
            SponsoredEvent.Report -> sheet.value?.let { report(it.messageId, ReportOptionId.Initial) }
            is SponsoredEvent.ChooseReportOption -> chooseOption(event.index)
            SponsoredEvent.BackToInfo -> backToInfo()
        }
    }

    private fun load() {
        if (loading?.isActive == true) return
        failed = false
        loading =
            scope.launch {
                when (val result = repository.sponsoredMessages(chatId)) {
                    is Outcome.Success -> {
                        loaded.value = LoadedAds(result.value)
                    }

                    is Outcome.Failure -> {
                        failed = true
                        log.warn("getChatSponsoredMessages failed: ${result.error::class.simpleName}")
                    }
                }
            }
    }

    private fun ad(id: Long): SponsoredMessage? = loaded.value?.byId?.get(id)

    private fun open(
        id: Long,
        isMediaClick: Boolean,
        url: String?,
    ) {
        val ad = ad(id) ?: return
        // Sent before this returns: opening the link leaves the app.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.click(chatId, id, isMediaClick).onFailure { error ->
                log.warn("clickChatSponsoredMessage failed: ${error::class.simpleName}")
            }
        }
        (url ?: ad.sponsor.url).takeIf(String::isNotBlank)?.let { onEffect(ChatEffect.OpenUrl(it)) }
    }

    private fun showInfo(id: Long) {
        val ad = ad(id) ?: return
        options = emptyList()
        sheet.value =
            SponsoredSheet(
                messageId = id,
                isRecommended = ad.isRecommended,
                sponsorInfo = ad.sponsor.info,
                additionalInfo = ad.additionalInfo,
                canBeReported = ad.canBeReported,
            )
    }

    private fun chooseOption(index: Int) {
        val open = sheet.value?.takeIf { it.report != null } ?: return
        val option = options.getOrNull(index) ?: return
        report(open.messageId, option.id)
    }

    private fun backToInfo() {
        options = emptyList()
        sheet.update { it?.copy(report = null, isReporting = false) }
    }

    /** One report at a time; the sheet shows it is on its way, then what TDLib asks for next, or closes. */
    private fun report(
        id: Long,
        option: ReportOptionId,
    ) {
        if (reporting?.isActive == true || ad(id)?.canBeReported != true) return
        sheet.update { it?.copy(isReporting = true) }
        reporting =
            scope.launch {
                when (val result = repository.report(chatId, id, option)) {
                    is Outcome.Success -> onReported(id, result.value)
                    is Outcome.Failure -> stopReporting(id, ChatEffect.ShowError(result.error))
                }
            }
    }

    private fun onReported(
        id: Long,
        result: SponsoredReportResult,
    ) {
        when (result) {
            is SponsoredReportResult.OptionRequired -> {
                options = result.options
                val step = SponsoredReportStep(result.title, result.options.map { it.text }.toImmutableList())
                sheet.update { open -> open?.takeIf { it.messageId == id }?.copy(report = step, isReporting = false) }
            }

            SponsoredReportResult.Reported -> {
                hidden.update { it.adding(id) }
                close(id, SponsoredReportNotice.Reported)
            }

            SponsoredReportResult.AdsHidden -> {
                hidden.update {
                    it.addingAll(
                        loaded.value
                            ?.byId
                            ?.keys
                            .orEmpty(),
                    )
                }
                close(id, SponsoredReportNotice.AdsHidden)
            }

            SponsoredReportResult.PremiumRequired -> {
                stopReporting(id, ChatEffect.SponsoredNotice(SponsoredReportNotice.PremiumRequired))
            }

            SponsoredReportResult.Failed -> {
                stopReporting(id, ChatEffect.SponsoredNotice(SponsoredReportNotice.ReportFailed))
            }
        }
    }

    private fun close(
        id: Long,
        notice: SponsoredReportNotice,
    ) {
        sheet.update { open -> open?.takeUnless { it.messageId == id } }
        onEffect(ChatEffect.SponsoredNotice(notice))
    }

    private fun stopReporting(
        id: Long,
        effect: ChatEffect,
    ) {
        sheet.update { open -> if (open?.messageId == id) open.copy(isReporting = false) else open }
        onEffect(effect)
    }

    /** What shows in [history] of [ads], without [hidden] ones; places the next one where it is due. */
    private fun place(
        history: ChatHistory,
        ads: LoadedAds,
        hidden: Set<Long>,
    ): List<PlacedSponsored> {
        val shown = ads.cards.filter { it.id !in hidden }
        if (ads.messagesBetween == 0) return listOfNotNull(shown.firstOrNull()?.let { PlacedSponsored(it, AT_END) })
        if (history.reachedEnd) placements.update { it.placingNext(history, ads.cards, ads.messagesBetween, hidden) }
        val placed = placements.value
        return shown.mapNotNull { card -> placed[card.id]?.let { PlacedSponsored(card, it) } }
    }

    /** What TDLib returned, with each message's card. */
    private class LoadedAds(
        ads: SponsoredMessages,
    ) {
        val messagesBetween = ads.messagesBetween
        val byId: Map<Long, SponsoredMessage> = ads.messages.associateBy { it.id }
        val cards: List<SponsoredCard> = ads.messages.map { it.toCard() }
    }

    companion object {
        /** The place of a sponsored message shown below every message (`messagesBetween` 0). */
        const val AT_END = Long.MIN_VALUE
    }
}

/**
 * Whether [chat] shows sponsored messages: a channel or a private chat with a bot, as `getChatSponsoredMessages`
 * serves only those; null while the user of a private chat is not known yet.
 */
internal fun showsSponsoredMessages(
    chat: Chat,
    users: Map<Long, User>,
): Boolean? =
    when (val type = chat.type) {
        is ChatType.Supergroup -> type.isChannel
        is ChatType.Private -> users[type.userId]?.type?.takeIf { it != UserType.Unknown }?.let { it == UserType.Bot }
        else -> false
    }

/** The card of a sponsored message: its text or caption, and its photo, video or GIF. */
internal fun SponsoredMessage.toCard(): SponsoredCard {
    val (text, media) =
        when (val content = content) {
            is MessageContent.Text -> content.text to null
            is MessageContent.Photo -> content.caption to content
            is MessageContent.Video -> content.caption to content
            is MessageContent.Animation -> content.caption to content
            else -> FormattedText.Empty to null
        }
    return SponsoredCard(
        id = id,
        isRecommended = isRecommended,
        title = title,
        text = text,
        media = media,
        sponsorPhoto = sponsor.photo,
        buttonText = buttonText,
    )
}
