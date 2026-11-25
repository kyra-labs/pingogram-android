package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Config

/** Reports Coil's `SubcomposeAsyncImage` in production code (`docs/PERFORMANCE.md` §4, "Images"). */
class PingogramSubcomposeAsyncImage(
    config: Config = Config.empty,
) : PingogramBannedName(
        config,
        id = "PingogramSubcomposeAsyncImage",
        banned = "SubcomposeAsyncImage",
        reason =
            "subcomposes each image's loading, success and error content in layout, which a list pays for every " +
                "row: use AsyncImage with placeholder and error painters.",
        callsOnly = true,
    )
