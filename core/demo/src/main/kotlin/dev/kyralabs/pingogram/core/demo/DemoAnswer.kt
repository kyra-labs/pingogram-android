package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import org.drinkless.tdlib.TdApi

/**
 * What the demo answers a TDLib function: its [result], the [updates] TDLib would send with it, and whether it
 * [changed] the demo, in which case the function goes into the journal and is replayed at the next start. [later]
 * holds what the other side does a moment after (a call ringing, then answered): run after its delay, never while the
 * journal is replayed, and kept in no journal.
 */
internal class DemoAnswer(
    val result: TdResult<TdApi.Object>,
    val updates: List<TdApi.Update> = emptyList(),
    val changed: Boolean = false,
    val later: List<DemoLater> = emptyList(),
)

/** A change [delayMillis] after an answer, on the demo's state, and the updates it sends. */
internal class DemoLater(
    val delayMillis: Long,
    val change: (DemoWorld) -> List<TdApi.Update>,
)

/** [this] answer with [later] changes after it. */
internal fun DemoAnswer.then(later: List<DemoLater>): DemoAnswer =
    DemoAnswer(
        result,
        updates,
        changed,
        this.later + later,
    )

/**
 * One area of TDLib's functions the demo answers (messages, chats, settings…), on the demo's state. It answers null
 * for a function it does not know, so the next area can; one none knows needs a Telegram account.
 */
internal fun interface DemoArea {
    fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer?
}

internal fun ok(value: TdApi.Object): DemoAnswer = DemoAnswer(TdResult.Success(value))

/** A change: [value] answered, [updates] sent, and the function kept in the journal. */
internal fun changed(
    value: TdApi.Object,
    updates: List<TdApi.Update>,
): DemoAnswer = DemoAnswer(TdResult.Success(value), updates, changed = true)

internal fun okChanged(updates: List<TdApi.Update>): DemoAnswer = changed(TdApi.Ok(), updates)

internal fun notFound(): DemoAnswer = DemoAnswer(TdResult.Failure(TdError(NOT_FOUND, "Not Found")))

internal fun badRequest(message: String): DemoAnswer = DemoAnswer(TdResult.Failure(TdError(BAD_REQUEST, message)))

/** What only Telegram's servers can answer and has no sensible sample: the app says it needs a Telegram account. */
internal fun needsAccount(): DemoAnswer = badRequest(DemoTdlibClient.NOT_AVAILABLE)

private const val BAD_REQUEST = 400
private const val NOT_FOUND = 404
