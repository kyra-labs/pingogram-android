package dev.kyralabs.pingogram.buildlogic

/**
 * Firebase for the `gms` flavor's push notifications (`docs/adr/0004-push-delivery.md`): the owner's
 * `google-services.json`, git-ignored, which the Google Services plugin turns into the resources Firebase reads. The
 * file belongs in the flavor's source set ([FLAVOR_FILE]), never in `app/` itself ([MISPLACED_FILE]), where the plugin
 * would hand Firebase's configuration to the `foss` variants too. Without it, configuration, debug builds of `foss`
 * and `./gradlew check` work as before; only packaging a `gms` variant fails, before anything runs.
 */
object GoogleServices {
    /** Relative to `:app`. */
    const val FLAVOR_FILE = "src/gms/google-services.json"
    const val MISPLACED_FILE = "google-services.json"

    /** The flavor the file is for; every other flavor stays free of Google's services ([GoogleFree]). */
    const val FLAVOR = "gms"

    /** What [resolve] found. */
    sealed interface Setup {
        /** Apply the Google Services plugin. */
        data object Configured : Setup

        /** Packaging a `gms` variant must fail with [message]. */
        data class Missing(
            val message: String,
        ) : Setup

        /** Configuration must fail with [message]. */
        data class Misplaced(
            val message: String,
        ) : Setup
    }

    /** [flavorFile], [misplacedFile]: whether `app/src/gms/google-services.json` and `app/google-services.json` exist. */
    fun resolve(
        flavorFile: Boolean,
        misplacedFile: Boolean,
    ): Setup =
        when {
            misplacedFile -> {
                Setup.Misplaced(
                    "app/$MISPLACED_FILE would configure Firebase for the foss flavor too: move it to " +
                        "app/$FLAVOR_FILE (docs/OWNER_SETUP.md, Push notifications).",
                )
            }

            flavorFile -> {
                Setup.Configured
            }

            else -> {
                Setup.Missing(
                    "Firebase is not configured: app/$FLAVOR_FILE is missing. The $FLAVOR flavor receives push " +
                        "notifications through it; download the file of your Firebase project (with Android apps for " +
                        "dev.kyralabs.pingogram and dev.kyralabs.pingogram.debug) to that path, where git ignores " +
                        "it, or let CI write it from the GOOGLE_SERVICES_JSON_BASE64 secret; see docs/OWNER_SETUP.md " +
                        "(Push notifications). foss builds and ./gradlew check do not need it.",
                )
            }
        }
}

/**
 * The check that keeps the `foss` flavor free of Google's proprietary services (`REQUIREMENTS.md` FR-N3,
 * `docs/adr/0004-push-delivery.md`): no Firebase, Play services, Google's data transport or Play Core module may be on
 * a `foss` variant's runtime classpath. Other `com.google.*` libraries (Dagger, Guava, ZXing) are open source and stay.
 */
object GoogleFree {
    /** Maven groups of Google's services; their subgroups (`com.google.android.gms.foo`) count too. */
    val SERVICE_GROUPS =
        listOf(
            "com.google.firebase",
            "com.google.android.gms",
            "com.google.android.datatransport",
            "com.google.android.play",
        )

    /** The [modules] (`group:artifact:version`) that belong to Google's services, in their order. */
    fun violations(modules: List<String>): List<String> =
        modules.filter { module ->
            val group = module.substringBefore(':')
            SERVICE_GROUPS.any { group == it || group.startsWith("$it.") }
        }

    fun message(
        variant: String,
        violations: List<String>,
    ): String =
        "$variant must not depend on Google's services (docs/adr/0004-push-delivery.md), but its runtime classpath " +
            "has ${violations.joinToString()}. Declare them for the ${GoogleServices.FLAVOR} flavor only " +
            "(\"${GoogleServices.FLAVOR}Implementation\"), or find what brings them in with " +
            "`./gradlew :app:dependencies --configuration ${variant}RuntimeClasspath`."
}
