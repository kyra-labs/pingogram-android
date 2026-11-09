package dev.kyralabs.pingogram.core.domain.model

/**
 * What the app knows about its own build, for About (`REQUIREMENTS.md` FR-S1) and the feedback report. `:app` provides
 * it from its `BuildConfig`, so features show it without depending on `:app`.
 *
 * @property versionName the version people see, e.g. `0.3.0` (`docs/RELEASE.md`: the tag without `v`).
 * @property versionCode the build's version code (`docs/RELEASE.md`), which tells the ABI split apart.
 * @property flavor `foss` or `gms` (`docs/ARCHITECTURE.md`, Build & modules).
 * @property isDebug a debug build.
 * @property installer the package of the app that installed Pingogram (Android's install source), or null when
 *   Android names none (installed from a file, or by `adb`).
 */
data class AppInfo(
    val versionName: String,
    val versionCode: Long = 0,
    val flavor: String = "",
    val isDebug: Boolean = false,
    val installer: String? = null,
) {
    /**
     * Installed from an app store that updates Pingogram by itself: Google Play, Huawei AppGallery or Samsung's Galaxy
     * Store (`docs/STORES.md`). About then leaves out "Check for updates", which points at GitHub's releases.
     */
    val isFromAppStore: Boolean get() = installer in APP_STORES

    private companion object {
        val APP_STORES =
            setOf(
                // Google Play.
                "com.android.vending",
                // Huawei AppGallery.
                "com.huawei.appmarket",
                // Samsung Galaxy Store.
                "com.sec.android.app.samsungapps",
            )
    }
}
