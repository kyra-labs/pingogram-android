package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening a photo from a conversation into the media viewer and closing it again (`docs/PERFORMANCE.md` §9, "Opening
 * and closing media"): the frames of the container transform both ways, and the app's `openMedia` section (the tap
 * until the viewer has drawn the photo's page) and `closeMedia` section (back until the viewer is gone). Each iteration
 * opens the newest photo of Saved Messages, the one chat the benchmarks open (opening a chat marks its messages as
 * read), waits for the viewer, presses back and waits for the viewer to go; the next one starts on the same
 * conversation. Needs a signed-in app on the device whose Saved Messages has a photo, without a caption, among its
 * newest messages (§9).
 */
@RunWith(AndroidJUnit4::class)
class OpenMediaBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openMediaCompilationNone() = openMedia(CompilationMode.None())

    @Test
    fun openMediaBaselineProfile() = openMedia(CompilationMode.Partial(BaselineProfileMode.Require))

    @OptIn(ExperimentalMetricApi::class)
    private fun openMedia(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics =
                listOf(
                    TraceSectionMetric(OPEN_MEDIA),
                    TraceSectionMetric(CLOSE_MEDIA),
                    TraceSectionMetric(OVERLAY_ENTER),
                    TraceSectionMetric(OVERLAY_EXIT),
                    FrameTimingMetric(),
                ),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                returnToChatList()
                openSavedMessages()
            },
        ) {
            openNewestPhoto()
            closeMediaViewer()
        }
    }
}

// :core:ui's MediaViewerTrace and OverlayTrace sections.
private const val OPEN_MEDIA = "openMedia"
private const val CLOSE_MEDIA = "closeMedia"
private const val OVERLAY_ENTER = "overlayEnter"
private const val OVERLAY_EXIT = "overlayExit"

private const val ITERATIONS = 10
