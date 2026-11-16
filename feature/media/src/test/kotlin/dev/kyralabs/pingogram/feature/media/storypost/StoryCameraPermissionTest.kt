package dev.kyralabs.pingogram.feature.media.storypost

import android.Manifest
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.FakeActivityResultRegistry
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * New story's "Camera" (#497): without the camera permission a tap asks for it and launches nothing, with it the camera
 * app opens, and a launch Android refuses says why in a snackbar. The view model's part (a camera file, then
 * `NewStoryEffect.LaunchCamera`, which the route hands to [StoryLaunchers.takePhoto]) is stood in for by the screen's
 * `onEvent`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryCameraPermissionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val registry = FakeActivityResultRegistry()
    private val snackbarHostState = SnackbarHostState()
    private val events = mutableListOf<NewStoryEvent>()

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                    val launchers = rememberStoryLaunchers({ events += it }, snackbarHostState, { Uri.parse(URI) })
                    NewStoryScreen(
                        state = NewStoryUiState(),
                        onEvent = { event ->
                            events += event
                            if (event == NewStoryEvent.TakePhoto) launchers.takePhoto(PATH)
                        },
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapCamera() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.new_story_camera)).performClick()
        composeRule.waitForIdle()
    }

    private fun grantCamera() =
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)

    @Test
    fun `Camera asks for the camera permission first and launches nothing`() {
        setScreen()

        tapCamera()

        assertEquals(
            Manifest.permission.CAMERA,
            registry.launchesOf<ActivityResultContracts.RequestPermission>().single().input,
        )
        assertTrue(registry.launchesOf<ActivityResultContracts.TakePicture>().isEmpty())
        assertTrue(events.none { it is NewStoryEvent.PhotoTaken })
    }

    @Test
    fun `with the permission Camera opens the camera app`() {
        grantCamera()
        setScreen()

        tapCamera()

        assertEquals(Uri.parse(URI), registry.launchesOf<ActivityResultContracts.TakePicture>().single().input)
    }

    @Test
    fun `a launch Android refuses says why and reports no photo`() {
        grantCamera()
        registry.beforeLaunch = { launch ->
            if (launch.contract is ActivityResultContracts.TakePicture) throw SecurityException("revoked permission")
        }
        setScreen()

        tapCamera()

        assertEquals(NewStoryEvent.PhotoTaken(taken = false), events.last())
        assertEquals(
            composeRule.activity.getString(UiR.string.photo_capture_permission_needed),
            snackbarHostState.currentSnackbarData?.visuals?.message,
        )
    }

    private companion object {
        const val PATH = "/cache/camera/photo.jpg"
        const val URI = "content://test/camera/photo.jpg"
    }
}
