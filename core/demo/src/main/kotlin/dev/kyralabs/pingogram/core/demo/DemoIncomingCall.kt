package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's own function, which no TDLib has: user [userId] calls the user a moment later
 * (`docs/adr/0034-demo-mode-for-store-review.md`), so the reviewers can answer a call. Only the demo's client takes
 * it; the call button's menu offers it in the demo. A TDLib object in form (public fields, a no-argument constructor),
 * so the journal keeps it as it keeps TDLib's functions.
 */
class DemoIncomingCall() : TdApi.Function<TdApi.Ok>() {
    @JvmField
    var userId: Long = 0

    constructor(userId: Long) : this() {
        this.userId = userId
    }

    override fun getConstructor(): Int = CONSTRUCTOR

    companion object {
        /** No TDLib constructor has it: TDLib's are CRC32s of td_api.tl's lines, and this one never reaches TDLib. */
        const val CONSTRUCTOR: Int = 0x44454d4f
    }
}
