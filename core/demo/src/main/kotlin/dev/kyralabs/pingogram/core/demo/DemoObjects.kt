package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import java.io.File

/** TDLib's file objects for the demo's [DemoFile]s: remote until written, then local and complete. */
internal object DemoFiles {
    fun remote(file: DemoFile): TdApi.File = state(file, local = null)

    fun state(
        file: DemoFile,
        local: File?,
    ): TdApi.File {
        val size = local?.length() ?: 0L
        val done = local != null
        return TdApi.File(
            file.id,
            size,
            size,
            // Path, can be downloaded, can be deleted, downloading, downloaded, offset, prefix and size.
            TdApi.LocalFile(local?.path.orEmpty(), true, done, false, done, 0, size, size),
            TdApi.RemoteFile("demo-${file.id}", "demo-${file.id}", false, true, size),
        )
    }
}

/** The polls of the demo, and the answers the user chooses in them. */
internal object DemoPolls {
    fun poll(
        id: Long,
        question: String,
        options: List<Pair<String, Int>>,
        chosen: Set<Int> = emptySet(),
    ): TdApi.Poll {
        val counts = options.mapIndexed { index, (_, votes) -> votes + if (index in chosen) 1 else 0 }
        val total = counts.sum()
        return TdApi.Poll().apply {
            this.id = id
            this.question = TdApi.FormattedText(question, emptyArray())
            this.options =
                options
                    .mapIndexed { index, (text, _) ->
                        TdApi.PollOption().apply {
                            this.id = index.toString()
                            this.text = TdApi.FormattedText(text, emptyArray())
                            voterCount = counts[index]
                            votePercentage = if (total == 0) 0 else counts[index] * PERCENT / total
                            recentVoterIds = emptyArray()
                            isChosen = index in chosen
                        }
                    }.toTypedArray()
            totalVoterCount = total
            recentVoterIds = emptyArray()
            canSeeResults = chosen.isNotEmpty()
            isAnonymous = true
            countryCodes = emptyArray()
            optionOrder = IntArray(options.size) { it }
            type = TdApi.PollTypeRegular()
        }
    }

    private const val PERCENT = 100
}

/** Notification settings as TDLib reports them; the demo never notifies, whatever they say. */
internal object DemoNotifications {
    fun chatSettings(muteFor: Int) =
        TdApi.ChatNotificationSettings(
            muteFor == 0,
            muteFor,
            true,
            0,
            true,
            true,
            true,
            false,
            true,
            0,
            true,
            true,
            true,
            false,
            true,
            false,
        )

    fun scopeSettings() = TdApi.ScopeNotificationSettings(0, 0, true, true, false, 0, true, false, false)
}
