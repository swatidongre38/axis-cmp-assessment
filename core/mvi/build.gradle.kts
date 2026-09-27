plugins {
    alias(libs.plugins.axis.kmp.library)
}

// Deliberately no Compose dependency here - this base class only needs coroutines and the
// multiplatform ViewModel + SavedStateHandle artifacts, so any future feature module can
// depend on it without pulling in UI concerns.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
