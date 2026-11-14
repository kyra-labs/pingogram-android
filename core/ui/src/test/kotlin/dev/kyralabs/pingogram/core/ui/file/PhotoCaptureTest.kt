package dev.kyralabs.pingogram.core.ui.file

import android.Manifest
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.FakeActivityResultRegistry
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.component.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Taking a photo with the camera app (#497): without the camera permission it asks first and launches once granted,
 * a refusal or a launch Android refuses says why in a snackbar and reports no photo, and the pending file outlives the
 * permission's dialog.
 */
@RunWith(RobolectricTestRunner::class)
class PhotoCaptureTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val registry = FakeActivityResultRegistry()
    private val snackbarHostState = SnackbarHostState()
    private val taken = mutableListOf<Boolean>()
    private lateinit var capture: PhotoCapture

    private fun setCapture(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                capture =
                    rememberPhotoCapture(
                        snackbarHostState = snackbarHostState,
                        noCameraAppMessage = NO_CAMERA_APP,
                        onTaken = { taken += it },
                        cameraUri = { path -> Uri.parse("content://test/$path") },
                    )
            }
        }
        if (restoration == null) composeRule.setContent(content) else restoration.setContent(content)
        composeRule.waitForIdle()
    }

    private fun take() {
        composeRule.runOnIdle { capture.take(PATH) }
        composeRule.waitForIdle()
    }

    private fun grantCamera() =
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)

    private fun permissionMessage() = composeRule.activity.getString(R.string.photo_capture_permission_needed)

    @Test
    fun `without the permission it asks for it and launches nothing`() {
        setCapture()

        take()

        val request = registry.launchesOf<ActivityResultContracts.RequestPermission>().single()
        assertEquals(Manifest.permission.CAMERA, request.input)
        assertTrue(registry.launchesOf<ActivityResultContracts.TakePicture>().isEmpty())
        assertTrue(taken.isEmpty())
    }

    @Test
    fun `once the permission is granted it launches the camera app into the file`() {
        setCapture()
        take()

        composeRule.runOnIdle { registry.answer(registry.launches.single(), true) }
        composeRule.waitForIdle()

        val launch = registry.launchesOf<ActivityResultContracts.TakePicture>().single()
        assertEquals(Uri.parse("content://test/$PATH"), launch.input)
    }

    @Test
    fun `a refused permission reports no photo and says why, with Settings once Android no longer asks`() {
        setCapture()
        take()

        composeRule.runOnIdle { registry.answer(registry.launches.single(), false) }
        composeRule.waitForIdle()

        assertEquals(listOf(false), taken)
        val visuals = snackbarHostState.currentSnackbarData?.visuals
        assertEquals(permissionMessage(), visuals?.message)
        assertEquals(composeRule.activity.getString(R.string.photo_capture_settings), visuals?.actionLabel)
        assertTrue(registry.launchesOf<ActivityResultContracts.TakePicture>().isEmpty())
    }

    @Test
    fun `with the permission it launches the camera app at once`() {
        grantCamera()
        setCapture()

        take()

        assertTrue(registry.launchesOf<ActivityResultContracts.RequestPermission>().isEmpty())
        assertEquals(1, registry.launchesOf<ActivityResultContracts.TakePicture>().size)
    }

    @Test
    fun `a launch Android refuses says why in a snackbar instead of crashing`() {
        grantCamera()
        registry.beforeLaunch = { launch ->
            if (launch.contract is ActivityResultContracts.TakePicture) throw SecurityException("revoked permission")
        }
        setCapture()

        take()

        assertEquals(listOf(false), taken)
        assertEquals(permissionMessage(), snackbarHostState.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun `no camera app says so`() {
        grantCamera()
        registry.beforeLaunch = { launch ->
            if (launch.contract is ActivityResultContracts.TakePicture) {
                throw android.content
                    .ActivityNotFoundException()
            }
        }
        setCapture()

        take()

        assertEquals(listOf(false), taken)
        assertEquals(NO_CAMERA_APP, snackbarHostState.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun `the file waits through the permission's dialog across process death`() {
        val restoration = StateRestorationTester(composeRule)
        setCapture(restoration)
        take()
        val request = registry.launches.single()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { registry.answer(request, true) }
        composeRule.waitForIdle()

        val launch = registry.launchesOf<ActivityResultContracts.TakePicture>().single()
        assertEquals(Uri.parse("content://test/$PATH"), launch.input)
        assertNull(snackbarHostState.currentSnackbarData)
    }

    private companion object {
        const val PATH = "/cache/camera/photo.jpg"
        const val NO_CAMERA_APP = "No camera app"
    }
}
