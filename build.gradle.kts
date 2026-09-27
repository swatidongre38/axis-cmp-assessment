// Plugins are declared once here (apply false) so every module shares one classloader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt)
}

// Static analysis for every module, not just one - a Lead's job includes making sure the
// whole team's code meets a bar, not just their own. buildUponDefaultConfig means our
// detekt.yml only needs to list what we're changing FROM the defaults, not restate everything.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        autoCorrect = false
    }
}
