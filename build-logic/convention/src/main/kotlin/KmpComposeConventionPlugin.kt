import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * KMP library + Compose Multiplatform on top, with the shared stability config wired in and
 * an opt-in compiler report (./gradlew <module>:assembleDebug -PcomposeCompilerReports=true).
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("axis.kmp.library")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("compose-stability.conf"),
                )
                if (providers.gradleProperty("composeCompilerReports").orNull == "true") {
                    val reportsDir = layout.buildDirectory.dir("compose_compiler")
                    reportsDestination.set(reportsDir)
                    metricsDestination.set(reportsDir)
                }
            }
        }
    }
}
