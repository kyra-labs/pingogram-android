package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Config

/** Reports `GlobalScope` in production code (CLAUDE.md hard rule 6). */
class PingogramGlobalScope(
    config: Config = Config.empty,
) : PingogramBannedName(
        config,
        id = "PingogramGlobalScope",
        banned = "GlobalScope",
        reason =
            "outlives every screen and account, and no test can wait for it: launch in a view model's scope, or " +
                "in the injected @ApplicationScope scope.",
        callsOnly = false,
    )
