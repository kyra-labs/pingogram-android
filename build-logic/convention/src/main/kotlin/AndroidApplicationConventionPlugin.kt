import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import dev.kyralabs.pingogram.buildlogic.PingogramAndroid
import dev.kyralabs.pingogram.buildlogic.PingogramVersion
import dev.kyralabs.pingogram.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "pingogram.android.lint")

            val version = PingogramVersion.parse(providers.gradleProperty("version").orNull)

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig {
                    targetSdk = PingogramAndroid.TARGET_SDK
                    versionName = version.name
                    versionCode = version.versionCode()
                }
            }

            // Split APKs get their own code (ABI_INDEX); outputs without an ABI filter are universal.
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.outputs.forEach { output ->
                        val abi =
                            output.filters
                                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                                ?.identifier
                        output.versionCode.set(version.versionCode(abi))
                    }
                }
            }
        }
    }
}
