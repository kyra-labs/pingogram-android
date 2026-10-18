// Plain Kotlin models and use cases; must never depend on Android (docs/ARCHITECTURE.md).
plugins {
    alias(libs.plugins.pingogram.jvm.library)
}

dependencies {
    api(projects.core.common)
    // Models hold ImmutableList/ImmutableMap, so repositories emit immutable snapshots (docs/PERFORMANCE.md §3–4).
    api(libs.kotlinx.collections.immutable)
}
