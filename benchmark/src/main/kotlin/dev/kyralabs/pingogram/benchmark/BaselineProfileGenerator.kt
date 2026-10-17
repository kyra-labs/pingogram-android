package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile and Startup Profile that `:app` ships (`docs/PERFORMANCE.md` §2). Run it through
 * `./gradlew :app:generateBaselineProfile`, which needs a device on API 33+ (or a rooted one on API 28+), and
 * commit the result in `app/src/main/generated/baselineProfiles/`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
        }
    }
}
