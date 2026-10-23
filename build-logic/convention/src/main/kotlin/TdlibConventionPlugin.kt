import dev.kyralabs.pingogram.buildlogic.PrebuiltAar
import dev.kyralabs.pingogram.buildlogic.usePrebuiltAar
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `:core:tdlib` only: puts the TDLib AAR (`org.drinkless:tdlib`, the generated `org.drinkless.tdlib` Java bindings
 * and `libtdjni.so`) on its `api` configuration, installed by [PrebuiltAar], and registers `fetchTdlib`.
 */
class TdlibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.usePrebuiltAar(PrebuiltAar.TDLIB, configuration = "api", taskName = "fetchTdlib")
    }
}
