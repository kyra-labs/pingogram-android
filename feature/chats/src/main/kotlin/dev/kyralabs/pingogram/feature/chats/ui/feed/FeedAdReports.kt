package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessage
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.ui.component.SponsoredReportStep
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "About this ad" of the feed's ads and their reports, as the conversation's (`ChatSponsoredMessages` of
 * `feature:chat`): who placed the ad and more about it ([show]), then reporting it with the reasons TDLib asks for,
 * one report at a time. A report that went through takes the ad away ([onHide]), or all of them once Telegram hid
 * ads for the user ([onHideAll]); what came of it is a notice ([FeedEffect.AdNotice]), failures an error.
 */
internal class FeedAdReports(
    private val actions: FeedCardActions,
    private val scope: CoroutineScope,
    private val onEffect: (FeedEffect) -> Unit,
    private val onHide: (id: Long) -> Unit,
    private val onHideAll: () -> Unit,
) {
    private val open = MutableStateFlow<OpenSheet?>(null)

    /** The options of the report step the sheet shows, by index. */
    private var options: List<ReportOption> = emptyList()
    private var reporting: Job? = null

    /** The open sheet and its ad's channel, or null. */
    val sheet: StateFlow<OpenSheet?> = open.asStateFlow()

    /** Opens "About this ad" of [ad], of channel [chatId]. */
    fun show(
        chatId: Long,
        ad: SponsoredMessage,
    ) {
        options = emptyList()
        open.value =
            OpenSheet(
                chatId,
                ad,
                SponsoredSheet(ad.id, ad.isRecommended, ad.sponsor.info, ad.additionalInfo, ad.canBeReported),
            )
    }

    fun onEvent(event: FeedAdEvent) {
        when (event) {
            FeedAdEvent.DismissInfo -> {
                open.value = null
            }

            FeedAdEvent.Report -> {
                report(ReportOptionId.Initial)
            }

            is FeedAdEvent.ChooseReportOption -> {
                options.getOrNull(event.index)?.let { report(it.id) }
            }

            FeedAdEvent.BackToInfo -> {
                options = emptyList()
                update { copy(report = null, isReporting = false) }
            }

            is FeedAdEvent.Viewed, is FeedAdEvent.Open, is FeedAdEvent.ShowInfo -> {
                // FeedSponsored handles these: they need the loaded ads.
            }
        }
    }

    /** Reports the open ad with [option]; the sheet shows it is on its way, then what TDLib asks next, or closes. */
    private fun report(option: ReportOptionId) {
        val shown = open.value?.takeIf { it.ad.canBeReported } ?: return
        if (reporting?.isActive == true) return
        update { copy(isReporting = true) }
        reporting =
            scope.launch {
                when (val result = actions.reportAd(shown.chatId, shown.ad.id, option)) {
                    is Outcome.Success -> onReported(shown.ad.id, result.value)
                    is Outcome.Failure -> stopReporting(FeedEffect.ShowError(result.error))
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
                update { copy(report = step, isReporting = false) }
            }

            SponsoredReportResult.Reported -> {
                onHide(id)
                close(FeedAdNotice.Reported)
            }

            SponsoredReportResult.AdsHidden -> {
                onHideAll()
                close(FeedAdNotice.AdsHidden)
            }

            SponsoredReportResult.PremiumRequired -> {
                stopReporting(FeedEffect.AdNotice(FeedAdNotice.PremiumRequired))
            }

            SponsoredReportResult.Failed -> {
                stopReporting(FeedEffect.AdNotice(FeedAdNotice.ReportFailed))
            }
        }
    }

    private fun close(notice: FeedAdNotice) {
        open.value = null
        onEffect(FeedEffect.AdNotice(notice))
    }

    private fun stopReporting(effect: FeedEffect) {
        update { copy(isReporting = false) }
        onEffect(effect)
    }

    private fun update(change: SponsoredSheet.() -> SponsoredSheet) {
        open.update { it?.copy(sheet = it.sheet.change()) }
    }

    /** "About this ad" of [ad], of channel [chatId], as it shows. */
    data class OpenSheet(
        val chatId: Long,
        val ad: SponsoredMessage,
        val sheet: SponsoredSheet,
    )
}
