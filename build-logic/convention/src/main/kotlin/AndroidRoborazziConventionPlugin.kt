import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HostTestBuilder
import dev.kyralabs.pingogram.buildlogic.PingogramAndroid
import dev.kyralabs.pingogram.buildlogic.RobolectricConfigTask
import dev.kyralabs.pingogram.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Roborazzi screenshot tests of Compose UI on Robolectric (`docs/TESTING.md`). Apply after
 * `pingogram.android.compose`.
 *
 * Goldens live in `src/test/snapshots`. `check` compares against them through `verifyRoborazziDebug`;
 * `recordRoborazziDebug` re-records them. Plain unit test runs skip the captures.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // A module without unit test sources is left alone until it gets some: Gradle fails a test
            // task whose only inputs are generated resources, and Roborazzi creates src/test/snapshots.
            if (listOf("src/test/kotlin", "src/test/java").none { file(it).exists() }) return

            apply(plugin = "io.github.takahirom.roborazzi")

            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/snapshots"))
            }
            tasks.named("check") { dependsOn("verifyRoborazziDebug") }

            extensions.getByType<CommonExtension>().testOptions.unitTests.apply {
                isIncludeAndroidResources = true
                all {
                    // Roborazzi renders with Robolectric's native graphics (real Skia, fonts and shapes).
                    it.systemProperty("robolectric.graphicsMode", "NATIVE")
                    // Robolectric's Android 16 runtime sets FileDescriptor internals through jdk.internal.access.
                    it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                }
            }

            // Default Robolectric SDK for every unit test of the variant (see PingogramAndroid.ROBOLECTRIC_SDK).
            extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                val unitTest =
                    (variant as? HasHostTests)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE) ?: return@onVariants
                val variantName = variant.name.replaceFirstChar(Char::uppercaseChar)
                val robolectricConfig =
                    tasks.register<RobolectricConfigTask>("generate${variantName}RobolectricConfig") {
                        sdk.set(PingogramAndroid.ROBOLECTRIC_SDK)
                    }
                unitTest.sources.resources?.addGeneratedSourceDirectory(
                    robolectricConfig,
                    RobolectricConfigTask::outputDirectory,
                )
            }

            dependencies {
                "testImplementation"(libs.findLibrary("robolectric").get())
                "testImplementation"(libs.findLibrary("roborazzi").get())
                "testImplementation"(libs.findLibrary("roborazzi-compose").get())
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "testImplementation"(libs.findLibrary("junit4").get())
                "testRuntimeOnly"(libs.findLibrary("junit-vintage-engine").get())
            }
        }
    }
}
