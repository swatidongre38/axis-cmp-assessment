import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * One place that decides how every shared KMP module is built - targets, JVM level, SDK
 * levels, namespace. Modules just declare what they need, not how the build is configured.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
            }
            val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                androidTarget {
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                }
                // Apple Silicon simulator + real devices. Add iosX64() too if Intel Macs need support.
                iosArm64()
                iosSimulatorArm64()
            }

            extensions.configure<LibraryExtension> {
                namespace = "com.axis.marketinsights." +
                    path.removePrefix(":").replace(':', '.').replace('-', '_')
                compileSdk = versionCatalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
                defaultConfig {
                    minSdk = versionCatalog.findVersion("android-minSdk").get().requiredVersion.toInt()
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }
}
