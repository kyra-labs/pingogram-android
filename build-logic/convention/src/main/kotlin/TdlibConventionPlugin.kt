import dev.kyralabs.pingogram.buildlogic.FetchTdlib
import dev.kyralabs.pingogram.buildlogic.TdlibAar
import dev.kyralabs.pingogram.buildlogic.TdlibAarSource
import dev.kyralabs.pingogram.buildlogic.TdlibAarSpec
import dev.kyralabs.pingogram.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.register

/**
 * `:core:tdlib` only: puts the TDLib AAR (`org.drinkless:tdlib`, the generated `org.drinkless.tdlib` Java bindings
 * and `libtdjni.so`) on its `api` configuration, installed by [TdlibAar], and registers `fetchTdlib`.
 */
class TdlibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val configure: TdlibAarSpec.() -> Unit = {
                repository.set(layout.settingsDirectory.dir(TdlibAar.REPOSITORY))
                commit.set(libs.findVersion(TdlibAar.COMMIT_CATALOG_KEY).get().requiredVersion)
                pinnedSha256.set(libs.findVersion(TdlibAar.SHA256_CATALOG_KEY).get().requiredVersion)
                // Relative to the repository root; File.resolve keeps an absolute path as it is.
                overrideAar.fileProvider(
                    providers
                        .gradleProperty(
                            TdlibAar.OVERRIDE_PROPERTY,
                        ).map { layout.settingsDirectory.asFile.resolve(it.trim()) },
                )
                offline.set(gradle.startParameter.isOffline)
            }

            // Obtained while :core:tdlib is configured (AGP looks at every declared dependency in afterEvaluate), and
            // again before each reuse of a configuration cache entry.
            val version = providers.of(TdlibAarSource::class) { parameters(configure) }
            pluginManager.withPlugin("com.android.library") {
                dependencies.addProvider("api", version.map { "${TdlibAar.GROUP}:${TdlibAar.NAME}:$it" })
            }

            tasks.register<FetchTdlib>("fetchTdlib") {
                group = "build setup"
                description =
                    "Downloads the pinned TDLib AAR (or copies ${TdlibAar.OVERRIDE_PROPERTY}) into ${TdlibAar.REPOSITORY}."
                configure()
            }
        }
    }
}
