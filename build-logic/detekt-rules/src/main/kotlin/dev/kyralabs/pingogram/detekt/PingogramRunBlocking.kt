package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Config

/**
 * Reports `runBlocking` in production code (`.claude/rules/kotlin.md`): it holds its thread until its coroutine ends.
 * Where a caller must have the result before it returns, the call is suppressed with the reason.
 */
class PingogramRunBlocking(
    config: Config = Config.empty,
) : PingogramBannedName(
        config,
        id = "PingogramRunBlocking",
        banned = "runBlocking",
        reason =
            "holds its thread until the coroutine ends: suspend instead. Where a caller must have the result " +
                "before it returns, suppress it with the reason.",
        callsOnly = true,
    )
