import com.android.build.api.dsl.LibraryExtension
import dev.kyralabs.pingogram.buildlogic.PingogramAndroid
import dev.kyralabs.pingogram.buildlogic.configureKotlinAndroid
import dev.kyralabs.pingogram.buildlogic.pingogramNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "pingogram.android.lint")
            apply(plugin = "pingogram.detekt")
            apply(plugin = "pingogram.ktlint")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = pingogramNamespace
                testOptions.targetSdk = PingogramAndroid.TARGET_SDK
                lint.targetSdk = PingogramAndroid.TARGET_SDK
            }
        }
    }
}
