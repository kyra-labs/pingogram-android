package dev.kyralabs.pingogram

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import dev.kyralabs.pingogram.background.KeepConnectedBootReceiver
import dev.kyralabs.pingogram.background.KeepConnectedService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The foss flavor's "Keep connected" in the merged manifest: a private `specialUse` foreground service with its
 * subtype explained (Android 14+), and a private boot receiver that is disabled until the setting is turned on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KeepConnectedManifestTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the service is private, of type specialUse, with its subtype explained`() {
        val service =
            context.packageManager.getServiceInfo(ComponentName(context, KeepConnectedService::class.java), 0)
        val declared =
            mergedManifest().elements("service").single {
                it.androidAttribute("name").endsWith(".KeepConnectedService")
            }
        val subtype =
            declared
                .elements("property")
                .single { it.androidAttribute("name") == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" }

        assertFalse(service.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
        assertTrue(subtype.androidAttribute("value").isNotBlank())
    }

    @Test
    fun `the boot receiver is private and disabled until keep connected is turned on`() {
        val receiver = ComponentName(context, KeepConnectedBootReceiver::class.java)
        val declared =
            mergedManifest().elements("receiver").single {
                it.androidAttribute("name").endsWith(".KeepConnectedBootReceiver")
            }
        val actions = declared.elements("action").map { it.androidAttribute("name") }

        assertEquals("false", declared.androidAttribute("enabled"))
        assertEquals("false", declared.androidAttribute("exported"))
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            context.packageManager.getComponentEnabledSetting(receiver),
        )
        assertEquals(
            listOf("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED"),
            actions,
        )
    }
}
