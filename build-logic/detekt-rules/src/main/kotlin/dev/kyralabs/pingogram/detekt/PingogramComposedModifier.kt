package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Config

/** Reports `Modifier.composed {}` in production code (`docs/PERFORMANCE.md` §4). */
class PingogramComposedModifier(
    config: Config = Config.empty,
) : PingogramBannedName(
        config,
        id = "PingogramComposedModifier",
        banned = "composed",
        reason =
            "composes its factory again for every element it modifies, and the modifier never compares equal: " +
                "write a Modifier.Node.",
        callsOnly = true,
    )
