package dev.kyralabs.pingogram.feature.chat.ui.location

import dev.kyralabs.pingogram.core.domain.model.Location
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** How the "Location" sheet writes coordinates and accuracy: hemispheres, five decimals, the language's units. */
@RunWith(RobolectricTestRunner::class)
class LocationTextTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `coordinates name their hemispheres, with five decimals`() {
        assertEquals(
            "33.86882° S, 151.20930° E",
            resources.coordinatesText(Location(-33.868820, 151.209296), Locale.US),
        )
        assertEquals(
            "40.71280 degrees north, 74.00600 degrees west",
            resources.coordinatesText(Location(40.7128, -74.006), Locale.US, spoken = true),
        )
    }

    @Test
    fun `digits follow the language`() {
        assertEquals("48,85837° N, 2,29448° E", resources.coordinatesText(Location(48.858370, 2.294481), Locale.FRANCE))
    }

    @Test
    fun `accuracy in whole metres, from a kilometre on in kilometres`() {
        assertEquals("12 m", accuracyText(12.4f, Locale.US))
        assertEquals("1 m", accuracyText(0.3f, Locale.US))
        assertEquals("1.5 km", accuracyText(1_520f, Locale.US))
    }
}
