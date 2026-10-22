package dev.kyralabs.pingogram.feature.feedback

import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport
import java.net.URLEncoder
import java.time.Instant

/**
 * The feedback report as plain text, as the user reads, copies and shares it: the build, the device, what the user
 * wrote ([description]), the last crash and the end of TDLib's log, in English for whoever reads the issue.
 */
internal fun DiagnosticReport.toText(description: String): String =
    buildString {
        appendLine("Pingogram feedback report")
        appendLine("Made: ${Instant.ofEpochMilli(generatedAtMillis)}")
        appendLine("App: $app")
        appendLine("Device: $device")
        appendLine()
        appendLine("What happened:")
        appendLine(description.trim().ifEmpty { "(not described)" })
        appendLine()
        append(crashText())
        appendLine()
        appendLine()
        append(logText(log))
    }.trimEnd()

private fun DiagnosticReport.crashText(): String {
    val crash = crash ?: return "Last crash: none kept"
    return "Last crash: ${Instant.ofEpochMilli(crash.timeMillis)} on thread ${crash.thread}\n${crash.trace}"
}

private fun logText(lines: List<String>): String =
    if (lines.isEmpty()) {
        "TDLib log: empty"
    } else {
        "TDLib log, last ${lines.size} lines (errors only):\n${lines.joinToString("\n")}"
    }

/**
 * A new issue on GitHub from the repository's bug report form (`.github/ISSUE_TEMPLATE/bug_report.yml`), its fields
 * filled from the report: version, device, the description as the steps, and the crash with as much of the log's end as
 * the address allows ([MAX_URL_LENGTH]; the shared report holds all of it). The browser opens it; nothing is sent until
 * the user submits it there, signed in to GitHub.
 */
internal fun DiagnosticReport.issueUrl(description: String): String {
    val fields =
        listOf(
            "template" to "bug_report.yml",
            "version" to app.removePrefix("Pingogram "),
            "device" to device,
            "steps" to description.trim().take(MAX_DESCRIPTION_IN_URL),
        )
    val base = ISSUES_URL + fields.joinToString("&", prefix = "?") { (key, value) -> "$key=${encode(value)}" }
    val crashPart = crash?.let { crashText() }
    // The log's newest lines matter most: as many of them as fit, oldest first.
    var logLines = log.size
    while (true) {
        val logs =
            listOfNotNull(
                crashPart,
                logText(log.takeLast(logLines)).takeIf { logLines > 0 },
            ).joinToString("\n\n")
        val url = if (logs.isEmpty()) base else "$base&logs=${encode(logs)}"
        if (url.length <= MAX_URL_LENGTH || logLines == 0) return url
        logLines--
    }
}

private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private const val ISSUES_URL = "https://github.com/kyra-labs/pingogram-android/issues/new"

/** The description's part of the address; the shared report holds all of it. */
private const val MAX_DESCRIPTION_IN_URL = 1500

/** What browsers and GitHub reliably take in an address. */
internal const val MAX_URL_LENGTH = 8000
