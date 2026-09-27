plugins {
    alias(libs.plugins.axis.kmp.library)
}

// Pure Kotlin: no Compose, no Android APIs. This is the module exported to Swift.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
