package dev.kyralabs.pingogram.core.ui.file

/** What came of handing a file to another app ([shareTdlibFile], [viewTdlibFile]). */
enum class ExternalFileResult {
    /** An app, or the system's chooser, was started with it. */
    Started,

    /** No app on the device takes it. */
    NoApp,

    /** It is not one of TDLib's downloaded files (or not there any more), so it is not handed out. */
    Unavailable,
}
