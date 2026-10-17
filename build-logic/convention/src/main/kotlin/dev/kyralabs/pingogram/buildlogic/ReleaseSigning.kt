package dev.kyralabs.pingogram.buildlogic

import java.io.StringReader
import java.util.Properties

/**
 * Release signing credentials per `docs/RELEASE.md`: the environment variables set by the release
 * workflow win over the git-ignored `keystore.properties`, and a source is only used when it is
 * complete. Passwords never appear in any message (CLAUDE.md rule 1).
 */
class ReleaseSigning(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    /** The signing to use, or why there is none (naming variables and keys, never values). */
    sealed interface Resolution {
        class Configured(
            val signing: ReleaseSigning,
        ) : Resolution

        class Missing(
            val message: String,
        ) : Resolution
    }

    companion object {
        const val PROPERTIES_FILE = "keystore.properties"

        // Same order as the constructor parameters.
        private val ENV_VARS =
            listOf("SIGNING_KEYSTORE_PATH", "SIGNING_KEYSTORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
        private val PROPERTY_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

        /** [env] looks up an environment variable; [keystoreProperties] is the file's text, or null if absent. */
        fun resolve(
            env: (String) -> String?,
            keystoreProperties: String?,
        ): Resolution {
            val properties = keystoreProperties?.let { Properties().apply { load(StringReader(it)) } }
            // Blank counts as unset: GitHub Actions passes an empty string for a secret that does not exist.
            val fromEnv = ENV_VARS.associateWith { env(it)?.takeIf(String::isNotBlank) }
            val fromFile = PROPERTY_KEYS.associateWith { properties?.getProperty(it)?.takeIf(String::isNotBlank) }

            (fromEnv.toSigning() ?: fromFile.toSigning())?.let { return Resolution.Configured(it) }

            // Only mention a source the user evidently tried to set up.
            val incomplete =
                listOfNotNull(
                    "environment (missing ${fromEnv.missing()})".takeIf { fromEnv.values.any { it != null } },
                    "$PROPERTIES_FILE (missing ${fromFile.missing()})".takeIf { properties != null },
                )
            val details = if (incomplete.isEmpty()) "" else incomplete.joinToString("; ", " Incomplete: ", ".")
            return Resolution.Missing(
                "Release signing is not configured. Set the environment variables ${ENV_VARS.joinToString()}, " +
                    "or create $PROPERTIES_FILE in the repository root with ${PROPERTY_KEYS.joinToString()}; " +
                    "see docs/RELEASE.md (Signing). Debug builds do not need this.$details",
            )
        }

        private fun Map<String, String?>.toSigning(): ReleaseSigning? {
            val (storeFile, storePassword, keyAlias, keyPassword) = values.toList()
            return if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
                ReleaseSigning(storeFile, storePassword, keyAlias, keyPassword)
            } else {
                null
            }
        }

        private fun Map<String, String?>.missing(): String = filterValues { it == null }.keys.joinToString()
    }
}
