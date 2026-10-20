package dev.kyralabs.pingogram

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gms flavor's Firebase Cloud Messaging in the merged manifest (docs/adr/0004-push-delivery.md): its permission to
 * receive messages, its receiver that only Play services may send to, the app's private messaging service, and
 * Firebase's automatic start and data collection off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirebaseMessagingManifestTest {
    private val manifest = mergedManifest()

    @Test
    fun `receives Firebase Cloud Messaging, only from Play services`() {
        val permissions = manifest.elements("uses-permission").map { it.androidAttribute("name") }
        val receiver =
            manifest.elements("receiver").single {
                it.androidAttribute("name") == "com.google.firebase.iid.FirebaseInstanceIdReceiver"
            }

        assertTrue("com.google.android.c2dm.permission.RECEIVE" in permissions)
        assertEquals("com.google.android.c2dm.permission.SEND", receiver.androidAttribute("permission"))
    }

    @Test
    fun `pushes reach the app's own service, which is private`() {
        val service =
            manifest.elements("service").single { it.androidAttribute("name").endsWith(".PingogramMessagingService") }
        val actions = service.elements("action").map { it.androidAttribute("name") }

        assertEquals("false", service.androidAttribute("exported"))
        assertEquals(listOf("com.google.firebase.MESSAGING_EVENT"), actions)
    }

    @Test
    fun `Firebase neither starts by itself nor collects anything`() {
        val metaData =
            manifest.elements("meta-data").associate { it.androidAttribute("name") to it.androidAttribute("value") }

        assertEquals("false", metaData["firebase_messaging_auto_init_enabled"])
        assertEquals("false", metaData["firebase_data_collection_default_enabled"])
        assertEquals("true", metaData["firebase_analytics_collection_deactivated"])
        assertEquals("false", metaData["firebase_messaging_delivery_metrics_exported_to_big_query_enabled"])
    }
}
