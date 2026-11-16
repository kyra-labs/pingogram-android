package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.SpringSpec
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneMotionDefaults
import androidx.compose.ui.unit.IntRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Panes move on the motion scheme's default spatial spring (`docs/DESIGN_SYSTEM.md` §5.1, Q-04): [paneMotion] reads
 * their bounds from the scheme, and material3-adaptive's own slides and expansions, which [SchemeAnimatedPane] keeps,
 * must have the same spring values. If a library update changes them, this fails and the slides need the scheme too.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PaneMotionTest {
    private val scheme = MotionScheme.expressive().defaultSpatialSpec<IntRect>() as SpringSpec<IntRect>

    @Test
    fun `the panes' bounds move on the scheme's default spatial spring`() {
        val bounds = PaneMotionSpecs(MotionScheme.expressive()).bounds as SpringSpec<IntRect>

        assertEquals(scheme.dampingRatio to scheme.stiffness, bounds.dampingRatio to bounds.stiffness)
    }

    @Test
    fun `material3-adaptive's pane slides have the scheme's default spatial values`() {
        // Its offset and size specs, which the slides and expansions use, are derived from this one.
        val library = PaneMotionDefaults.AnimationSpec as SpringSpec<IntRect>

        assertEquals(scheme.dampingRatio to scheme.stiffness, library.dampingRatio to library.stiffness)
    }
}
