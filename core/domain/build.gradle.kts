// Plain Kotlin models and use cases; must never depend on Android (docs/ARCHITECTURE.md).
plugins {
    alias(libs.plugins.pingogram.jvm.library)
}

dependencies {
    api(projects.core.common)
}
