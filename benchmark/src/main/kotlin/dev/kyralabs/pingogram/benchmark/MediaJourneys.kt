package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * The media viewer's journeys (`OpenMediaBenchmark`, docs/PERFORMANCE.md §9 "Opening and closing media"), beside
 * Journeys.kt's: they need a signed-in app whose open conversation shows a photo.
 */

/**
 * Opens the newest photo of the open conversation in the media viewer, as a tap on its bubble does, and waits for the
 * viewer. The bubbles' semantics are merged, so the photo is found through its row: the lowest row whose description
 * names a photo, tapped near its end, where an outgoing bubble's media is (the benchmarks open Saved Messages, whose
 * messages are all the user's own), a third of the way down, clear of a caption below the photo.
 */
internal fun MacrobenchmarkScope.openNewestPhoto(): UiObject2 {
    val list = messageList()
    val row =
        device
            .findObjects(By.desc(PHOTO_ROW))
            .filter { list.visibleBounds.contains(it.visibleBounds) }
            .maxByOrNull { it.visibleBounds.top }
            ?: error("No photo in the conversation on screen. Send a photo to Saved Messages first.")
    val bounds = row.visibleBounds
    val inset = (PHOTO_TAP_INSET_DP * device.displayDensity()).toInt()
    device.click(bounds.right - inset, bounds.top + bounds.height() / PHOTO_TAP_HEIGHT_FRACTION)
    return device.wait(Until.findObject(By.res(MEDIA_VIEWER)), TIMEOUT_MS)
        ?: error("The media viewer did not open within $TIMEOUT_MS ms.")
}

/** Closes the media viewer with back and waits until it is gone. */
internal fun MacrobenchmarkScope.closeMediaViewer() {
    device.pressBack()
    if (!device.wait(Until.gone(By.res(MEDIA_VIEWER)), TIMEOUT_MS)) {
        error("The media viewer did not close within $TIMEOUT_MS ms.")
    }
    device.waitForIdle()
}

/** The display's density, from the instrumentation's view of the app. */
@Suppress("UnusedReceiverParameter") // Read as the device's, beside its display size.
private fun UiDevice.displayDensity(): Float =
    InstrumentationRegistry
        .getInstrumentation()
        .targetContext.resources.displayMetrics.density

// The tag in feature:media (MediaViewerScreen.kt).
private const val MEDIA_VIEWER = "media_viewer"

/** A bubble row whose description (sender, time, then its content) names a photo (core:ui's "Photo"). */
private val PHOTO_ROW: Pattern = Pattern.compile(".*\\bPhoto\\b.*", Pattern.DOTALL)

/** Where the tap on a photo's row lands: this far from the row's end, a third of the way down. */
private const val PHOTO_TAP_INSET_DP = 48
private const val PHOTO_TAP_HEIGHT_FRACTION = 3

/** How long the journeys wait for the viewer: the photo's page comes from TDLib's memory. */
private const val TIMEOUT_MS = 10_000L
