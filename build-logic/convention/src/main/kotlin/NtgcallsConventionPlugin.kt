import dev.kyralabs.pingogram.buildlogic.PrebuiltAar
import dev.kyralabs.pingogram.buildlogic.usePrebuiltAar
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `:feature:calls` only: puts the call engine's AAR (`io.github.pytgcalls:ntgcalls`, its Java API, WebRTC's Java
 * classes and `libntgcalls.so`) on its `implementation` configuration, installed by [PrebuiltAar], and registers
 * `fetchNtgcalls` (`docs/adr/0015-call-media-engine.md`).
 */
class NtgcallsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.usePrebuiltAar(PrebuiltAar.NTGCALLS, configuration = "implementation", taskName = "fetchNtgcalls")
    }
}
