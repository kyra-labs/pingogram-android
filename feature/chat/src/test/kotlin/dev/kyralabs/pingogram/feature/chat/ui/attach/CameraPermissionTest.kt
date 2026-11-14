package dev.kyralabs.pingogram.feature.chat.ui.attach

import android.Manifest
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.FakeActivityResultRegistry
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.composer.CameraButton
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import kotlinx.collections.immutable.persistentListOf
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
 * The conversation's two ways to the camera app, the composer's camera button and the attach menu's "Camera" (#497):
 * without the camera permission a tap asks for it and launches nothing, with it the camera app opens, and a launch
 * Android refuses says why in a snackbar. The view model's part (a camera file, then `ChatEffect.LaunchCamera`, which
 * the route hands to [AttachLaunchers.takePhoto]) is stood in for by [onEvent].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CameraPermissionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val registry = FakeActivityResultRegistry()
    private val snackbarHostState = SnackbarHostState()
    private val events = mutableListOf<ChatEvent>()

    private fun setContent(entry: @Composable (AttachInputs) -> Unit) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                    lateinit var launchers: AttachLaunchers
                    val onEvent: (ChatEvent) -> Unit = { event ->
                        events += event
                        if (event == ChatEvent.TakePhoto) launchers.takePhoto(PATH)
                    }
                    launchers = rememberAttachLaunchers(onEvent, snackbarHostState, cameraUri = { Uri.parse(URI) })
                    val inputs =
                        rememberAttachInputs(
                            launchers = launchers,
                            onEvent = onEvent,
                            caption = ComposerText(),
                            pollEditor = PollEditor(),
                            snackbarHostState = snackbarHostState,
                        )
                    Column { entry(inputs) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val cameraButton: @Composable (AttachInputs) -> Unit = { inputs ->
        CameraButton(onCamera = { inputs.onPick(AttachOption.Camera) })
    }

    private val attachMenu: @Composable (AttachInputs) -> Unit = { inputs ->
        AttachOptions(persistentListOf(AttachOption.Gallery, AttachOption.Camera), onPick = inputs.onPick)
    }

    private fun tapCameraButton() =
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.attach_camera)).performClick()

    // The tile's label is hidden from screen readers, which hear the button by its icon's description.
    private fun tapMenuCamera() = tapCameraButton()

    private fun grantCamera() =
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)

    private fun assertAskedForThePermissionOnly() {
        composeRule.waitForIdle()
        assertEquals(
            Manifest.permission.CAMERA,
            registry.launchesOf<ActivityResultContracts.RequestPermission>().single().input,
        )
        assertTrue(registry.launchesOf<ActivityResultContracts.TakePicture>().isEmpty())
        assertTrue(events.none { it is ChatEvent.PhotoTaken })
    }

    @Test
    fun `the camera button asks for the camera permission first`() {
        setContent(cameraButton)

        tapCameraButton()

        assertAskedForThePermissionOnly()
    }

    @Test
    fun `the attach menu's Camera asks for the camera permission first`() {
        setContent(attachMenu)

        tapMenuCamera()

        assertAskedForThePermissionOnly()
    }

    @Test
    fun `with the permission the camera button opens the camera app`() {
        grantCamera()
        setContent(cameraButton)

        tapCameraButton()
        composeRule.waitForIdle()

        assertEquals(Uri.parse(URI), registry.launchesOf<ActivityResultContracts.TakePicture>().single().input)
    }

    @Test
    fun `with the permission the attach menu's Camera opens the camera app`() {
        grantCamera()
        setContent(attachMenu)

        tapMenuCamera()
        composeRule.waitForIdle()

        assertEquals(1, registry.launchesOf<ActivityResultContracts.TakePicture>().size)
    }

    @Test
    fun `a launch Android refuses says why and reports no photo`() {
        grantCamera()
        registry.beforeLaunch = { launch ->
            if (launch.contract is ActivityResultContracts.TakePicture) throw SecurityException("revoked permission")
        }
        setContent(cameraButton)

        tapCameraButton()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.PhotoTaken(taken = false), events.last())
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
