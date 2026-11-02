package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * A demo client on the test's scheduler, with a journal in memory that outlives it (as the file outlives a process),
 * so a test can make a second client to see what a restart keeps. Files are written as their names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DemoTestHarness(
    private val directory: File,
) {
    private val lines = mutableListOf<String>()

    private val journal =
        DemoJournal(
            object : DemoJournalStorage {
                override fun read() = lines.toList()

                override fun append(line: String) {
                    lines += line
                }
            },
        )

    fun TestScope.start(): Pair<DemoTdlibClient, MutableList<TdApi.Update>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher)
        val client =
            DemoTdlibClient(
                text = { "Text $it" },
                journal = journal,
                directory = directory,
                fileWriter = { file, target ->
                    target.parentFile?.mkdirs()
                    target.writeText(file.fileName)
                },
                scope = scope,
                io = dispatcher,
                clock = { NOW },
            )
        val updates = mutableListOf<TdApi.Update>()
        scope.launch { client.updates.collect { updates += it } }
        runCurrent()
        client.start()
        runCurrent()
        return client to updates
    }

    companion object {
        const val NOW = 1_800_000_000
    }
}

internal suspend fun <T : TdApi.Object> DemoTdlibClient.ask(function: TdApi.Function<T>): T =
    (send(function) as TdResult.Success).value

internal fun plainText(text: String) = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, false)
