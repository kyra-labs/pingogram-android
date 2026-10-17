package dev.kyralabs.pingogram.buildlogic

/**
 * App version from the `-Pversion=` Gradle property (set by CI from the tag), per `docs/RELEASE.md`.
 *
 * `versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + ABI_INDEX`, so per-ABI APKs
 * get distinct, ordered codes and the universal APK is the lowest.
 */
data class PingogramVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val name: String,
) {
    /** [abi] is the ABI filter of a split APK, or `null` for the universal APK. */
    fun versionCode(abi: String? = null): Int {
        val abiIndex = ABI_INDEX[abi] ?: error("No versionCode index for ABI '$abi'. Known: ${ABI_INDEX.keys}")
        // Android needs a positive versionCode; only the local 0.0.0-dev fallback would hit 0.
        return (major * 1_000_000 + minor * 10_000 + patch * 100 + abiIndex).coerceAtLeast(1)
    }

    companion object {
        const val DEV_VERSION = "0.0.0-dev"

        val ABI_INDEX: Map<String?, Int> = mapOf(
            null to 0,
            "armeabi-v7a" to 1,
            "arm64-v8a" to 2,
            "x86_64" to 3,
        )

        private val SEMVER = Regex("""(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?""")

        /** Parses `X.Y.Z` or `X.Y.Z-suffix` (a leading `v` is accepted); null or blank means [DEV_VERSION]. */
        fun parse(raw: String?): PingogramVersion {
            val name = raw?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: DEV_VERSION
            val match = SEMVER.matchEntire(name)
                ?: error("Invalid -Pversion='$raw'. Expected MAJOR.MINOR.PATCH[-suffix], e.g. 0.2.0 or 1.0.0-beta.1")
            val (major, minor, patch) = match.destructured.toList().take(3).map(String::toInt)
            require(minor < 100 && patch < 100) {
                "Invalid -Pversion='$raw'. MINOR and PATCH must be below 100 to fit the versionCode formula"
            }
            require(major < 2_000) { "Invalid -Pversion='$raw'. MAJOR must be below 2000 to fit in an Int" }
            return PingogramVersion(major, minor, patch, name)
        }
    }
}
