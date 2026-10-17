package dev.kyralabs.pingogram.core.tdlib

import java.util.Collections

internal class RecordingTdlibLog : TdlibLog {
    val messages: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // Evaluated like a debug build does, which also checks that no TdApi toString() (native) is called.
    override fun debug(message: () -> String) {
        messages += message()
    }

    override fun warn(
        message: String,
        error: Throwable?,
    ) {
        messages += message
    }
}
