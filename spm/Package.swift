// swift-tools-version:5.9
// Distribution of the shared KMP framework to a separate iOS repo via Swift Package Manager.
//
// CI (on tag):
//   ./gradlew :composeApp:assembleComposeAppReleaseXCFramework
//   cd composeApp/build/XCFrameworks/release && zip -r ComposeApp.xcframework.zip ComposeApp.xcframework
//   swift package compute-checksum ComposeApp.xcframework.zip   # paste below
//   upload the zip as a GitHub Release asset
// Tools such as KMMBridge automate these steps.
import PackageDescription

let package = Package(
    name: "ComposeApp",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "ComposeApp", targets: ["ComposeApp"])
    ],
    targets: [
        .binaryTarget(
            name: "ComposeApp",
            url: "https://github.com/<your-github-user>/axis-market-insights-cmp/releases/download/0.1.0/ComposeApp.xcframework.zip",
            checksum: "<output of swift package compute-checksum>"
        )
    ]
)
