package dev.kyralabs.pingogram.feature.chat.ui

/** The copies each overlay stage of the conversation's state keeps from one state to the next ([OverlayCopies]). */
internal class OverlayStages {
    val translations = OverlayCopies()
    val sponsored = OverlayCopies()
    val search = OverlayCopies()
    val keyword = OverlayCopies()
}
