import com.android.build.api.dsl.CommonExtension
import dev.kyralabs.pingogram.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Apply after `pingogram.android.application` or `pingogram.android.library`. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            dependencies {
                val bom = platform(libs.findLibrary("androidx-compose-bom").get())
                "implementation"(bom)
                "androidTestImplementation"(bom)
                "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
