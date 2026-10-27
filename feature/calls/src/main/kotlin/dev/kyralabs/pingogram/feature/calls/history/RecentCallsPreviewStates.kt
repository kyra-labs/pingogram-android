package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.calls.R
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The states of Recent calls, shared by the previews and the screenshot tests. */
internal object RecentCallsPreviewStates {
    @Suppress("LongParameterList") // A preview row's fields, named where each row is made.
    private fun call(
        id: Long,
        name: String,
        outcome: CallOutcome,
        isOutgoing: Boolean,
        isVideo: Boolean,
        time: String,
        duration: String? = null,
    ) = RecentCallsItem.Call(
        key = "$id/$id",
        chatId = id,
        messageId = id,
        userId = id,
        name = UiText.Plain(name),
        photo = null,
        outcome = outcome,
        isOutgoing = isOutgoing,
        isVideo = isVideo,
        kind = UiText.resource(kindOf(outcome, isOutgoing, isVideo)),
        time = time,
        duration = duration,
    )

    private fun kindOf(
        outcome: CallOutcome,
        isOutgoing: Boolean,
        isVideo: Boolean,
    ): Int {
        val (voice, video) =
            when {
                outcome == CallOutcome.Missed -> {
                    UiR.string.preview_call_missed to UiR.string.preview_video_call_missed
                }

                outcome == CallOutcome.Cancelled -> {
                    UiR.string.preview_call_cancelled to UiR.string.preview_video_call_cancelled
                }

                outcome == CallOutcome.Declined -> {
                    UiR.string.preview_call_declined to UiR.string.preview_video_call_declined
                }

                isOutgoing -> {
                    UiR.string.preview_call_outgoing to UiR.string.preview_video_call_outgoing
                }

                else -> {
                    UiR.string.preview_call_incoming to UiR.string.preview_video_call_incoming
                }
            }
        return if (isVideo) video else voice
    }

    private val items =
        persistentListOf(
            RecentCallsItem.Day("day-today", UiText.resource(R.string.recent_calls_today)),
            call(1, "Grace Hopper", CallOutcome.Missed, isOutgoing = false, isVideo = false, time = "9:41 AM"),
            call(2, "Alan Turing", CallOutcome.Answered, true, isVideo = true, time = "8:15 AM", duration = "12:04"),
            call(7, "Grace Hopper", CallOutcome.Answered, isOutgoing = false, isVideo = false, time = "8:02 AM").copy(
                kind = UiText.resource(UiR.string.preview_group_call_active),
                isGroupCall = true,
                isActive = true,
            ),
            RecentCallsItem.Day("day-yesterday", UiText.resource(R.string.recent_calls_yesterday)),
            call(3, "Ada Lovelace", CallOutcome.Answered, false, isVideo = false, time = "6:30 PM", duration = "4:05"),
            call(4, "Katherine Johnson", CallOutcome.Cancelled, true, isVideo = false, time = "2:02 PM"),
            call(5, "Margaret Hamilton", CallOutcome.Declined, false, isVideo = true, time = "11:47 AM"),
            RecentCallsItem.Day("day-0317", UiText.Plain("Monday, March 17")),
            call(6, "Hedy Lamarr", CallOutcome.Answered, true, isVideo = false, time = "7:58 PM", duration = "1:02:03"),
        )

    val loading = RecentCallsUiState(CallFilter.All, RecentCallsContent.Loading)

    val calls =
        RecentCallsUiState(
            CallFilter.All,
            RecentCallsContent.Calls(items, loadingMore = false, pageError = null, reachedEnd = true),
        )

    val loadingMore =
        RecentCallsUiState(
            CallFilter.All,
            RecentCallsContent.Calls(items, loadingMore = true, pageError = null, reachedEnd = false),
        )

    val pageError =
        RecentCallsUiState(
            CallFilter.All,
            RecentCallsContent.Calls(items, loadingMore = false, pageError = DomainError.Network, reachedEnd = false),
        )

    val empty = RecentCallsUiState(CallFilter.All, RecentCallsContent.Empty)

    val noMissed = RecentCallsUiState(CallFilter.Missed, RecentCallsContent.Empty)

    val error = RecentCallsUiState(CallFilter.All, RecentCallsContent.Error(DomainError.Network))
}

@Composable
private fun Preview(state: RecentCallsUiState) {
    PingogramPreview { RecentCallsScreen(state, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun RecentCallsPreview() = Preview(RecentCallsPreviewStates.calls)

@PingogramPreviews
@Composable
private fun RecentCallsLoadingPreview() = Preview(RecentCallsPreviewStates.loading)

@PingogramPreviews
@Composable
private fun RecentCallsLoadingMorePreview() = Preview(RecentCallsPreviewStates.loadingMore)

@PingogramPreviews
@Composable
private fun RecentCallsPageErrorPreview() = Preview(RecentCallsPreviewStates.pageError)

@PingogramPreviews
@Composable
private fun RecentCallsEmptyPreview() = Preview(RecentCallsPreviewStates.empty)

@PingogramPreviews
@Composable
private fun RecentCallsNoMissedPreview() = Preview(RecentCallsPreviewStates.noMissed)

@PingogramPreviews
@Composable
private fun RecentCallsErrorPreview() = Preview(RecentCallsPreviewStates.error)
