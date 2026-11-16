package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.PublicPostSearchLimits
import org.drinkless.tdlib.TdApi

/** TDLib's limits of public post search (`publicPostSearchLimits`) as the domain's. */
internal fun TdApi.PublicPostSearchLimits.toLimits() =
    PublicPostSearchLimits(
        dailyFreeQueries = dailyFreeQueryCount,
        remainingFreeQueries = remainingFreeQueryCount,
        nextFreeQueryInSeconds = nextFreeQueryIn,
        starCount = starCount,
        isQueryFree = isCurrentQueryFree,
    )
