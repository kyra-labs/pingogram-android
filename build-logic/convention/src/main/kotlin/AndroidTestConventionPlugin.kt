import com.android.build.api.dsl.TestExtension
import dev.kyralabs.pingogram.buildlogic.PingogramAndroid
import dev.kyralabs.pingogram.buildlogic.configureAndroidCompilation
import dev.kyralabs.pingogram.buildlogic.pingogramNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * A `com.android.test` module that instruments another module's APK, e.g. `:benchmark` (Macrobenchmark).
 * No `pingogram.android.lint`: AGP has no lint tasks for test modules.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.test")

            extensions.configure<TestExtension> {
                configureAndroidCompilation(this)
                namespace = pingogramNamespace
                defaultConfig {
                    targetSdk = PingogramAndroid.TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            // The tests need a device, so `check` only compiles them; that still catches breakage in CI.
            tasks.named("check") { dependsOn(tasks.withType<KotlinJvmCompile>()) }
        }
    }
}
