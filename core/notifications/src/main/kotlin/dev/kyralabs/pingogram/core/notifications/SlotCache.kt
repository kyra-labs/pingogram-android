package dev.kyralabs.pingogram.core.notifications

import java.util.concurrent.ConcurrentHashMap

/**
 * Values made for an account's slot, kept while the same account ([owner], by identity) holds the slot: an account
 * that takes a slot another one left gets values of its own, made anew, and the old account's are dropped with it
 * (`docs/audit/performance-review-2026-11.md`, W4: call notifications drew a new account's callers through the removed
 * account's closed client). Safe to use from several threads.
 */
internal class SlotCache<O : Any, V : Any> {
    private val made = ConcurrentHashMap<Int, Pair<O, V>>()

    /** The value of [slot] made for [owner], or a new one from [create] when the slot is new or another's. */
    fun get(
        slot: Int,
        owner: O,
        create: () -> V,
    ): V =
        made
            .compute(slot) { _, current -> current?.takeIf { it.first === owner } ?: (owner to create()) }!!
            .second
}
