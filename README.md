# Axis Market Insights & Execution: Compose Multiplatform

Lead CMP technical assessment. A single shared screen (Android + iOS) showing a high-frequency mock feed of bonds and equities, live-linked analyst research calls, and a validated order ticket.

| Deliverable | Where |
|---|---|
| Multi-module Gradle (Kotlin DSL, version catalog, convention plugins) | `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/` |
| Live market UI + research calls | `feature/market` |
| Performance techniques | see table below |
| Shared business logic (`TradeValidator`) | `core/domain/.../order/TradeValidator.kt` |
| Swift interop + SPM | `iosApp/iosApp/TradeValidatorInterop.swift`, `spm/Package.swift` |
| ADR / tech spec | `docs/ADR-001-market-insights-architecture.md` |
| Code review of junior PR | `docs/CODE_REVIEW.md` |

## Module graph

```
composeApp  (Android app + iOS framework "ComposeApp", exports :core:domain to Swift)
 ├── feature:market     MarketViewModel extends BaseViewModel, Compose UI
 │     ├── core:mvi            generic State/Intent/Effect ViewModel base, SavedStateHandle-aware
 │     ├── core:designsystem   theme, market colours, tabular numbers
 │     └── core:domain
 ├── core:data          DTOs + mappers, mock feed, conflating repository, mock research/order repos
 │     └── core:domain
 ├── core:mvi           BaseViewModel<State, Intent, Effect> - no Compose dependency
 └── core:domain        pure Kotlin: models, FixedPoint, TradeValidator, contracts (no Compose, no Android)
build-logic/convention  axis.kmp.library, axis.kmp.compose
config/detekt           static analysis config, enforced in CI on every module
```

Separate Gradle modules rather than one `shared:kmp` module, on purpose: `core:domain`'s
build script has zero dependency on `core:data`, so a UI-layer class reaching into a
repository implementation directly isn't a code-review catch, it's a compile error. That
trade-off (more module-wiring ceremony, in exchange for the dependency rule being enforced
by the compiler rather than by convention) is the one I'd make as the person setting the
architecture for a team, not just writing a demo.

**DTOs and mapping:** `core/data/dto` holds the wire-shaped objects (`ResearchCallDto`,
`InstrumentDto`) as they'd arrive from a real backend - primitives only, no domain types.
`core/data/mapper` converts them to domain models (`ResearchCall`, `Instrument`), dropping
and logging anything malformed instead of crashing. `RawSeedSource` plays the role of an
already-parsed JSON response body, so `MockResearchRepository` exercises the same
DTO → mapper → domain path a real HTTP client would. `Tick` is the one exception - it's
generated in-process by `MockMarketFeed`, not parsed from JSON, so it stays a plain domain
type without a DTO counterpart until there's an actual wire format to map from.

**Base ViewModel:** `core/mvi`'s `BaseViewModel<State, Intent, Effect>` owns the
`MutableStateFlow`/`Channel` plumbing every screen needs, so `MarketViewModel` only
declares what's specific to this screen (its state shape, its intents, `onIntent`). The one
deliberate exception is the `quotes` fast-lane `StateFlow` - `BaseViewModel` intentionally
supports exactly one `state` stream, and mixing a 20Hz stream into it would invalidate every
part of the screen on every tick, so `MarketViewModel` declares that stream itself
alongside what the base class provides.

**Process death, not just rotation:** `BaseViewModel` optionally takes a `SavedStateHandle`.
A rotation is already handled for free by ViewModel scoping, but the OS killing the whole
process in the background (common on low-memory devices) is not - an in-memory
`MutableStateFlow` doesn't survive that. `MarketViewModel` persists just the open ticket's
instrument id and side (not the whole `OrderTicketState`, which isn't trivially saveable),
and rebuilds the ticket once a live `Quote` for that instrument is available again after
restart. `App.kt` wires this through the standard `viewModelFactory { initializer { ... } }`
+ `createSavedStateHandle()` pattern.

**Static analysis:** `detekt` runs across every module (`subprojects {}` in the root build
script), configured in `config/detekt/detekt.yml`, and fails CI on any issue
(`build.maxIssues = 0`). A couple of default rules are turned off with a comment explaining
why (e.g. `MagicNumber` for the seed data and layout math, which is legitimately full of
specific literals) - the goal is a bar the whole team can meet, not zero-tolerance theatre.

## Performance: what keeps 500 ticks/s smooth

| Technique | Where |
|---|---|
| Explicit backpressure: every tick folded into state, UI sampled at 50 ms | `DefaultMarketRepository` |
| Persistent (structurally shared) collections: unchanged quotes keep identity | `QuoteBook` |
| Two state lanes; fast lane read only in leaf composables via `() -> QuotesUiState` | `MarketRoute`, `QuotesList`, `FeedStats` |
| Domain models declared stable via `compose-stability.conf` (no Compose dep in domain) | root, `KmpComposeConventionPlugin` |
| `LazyColumn` with `key` + `contentType` | `QuotesList` |
| Tick flash animated in the draw phase only (`drawBehind` reads `Animatable`) | `QuoteRow` |
| Single-pass custom `Layout` with fixed numeric columns, no intrinsics | `QuoteRowLayout` |
| `drawWithCache` sparkline: Path rebuilt only when data changes | `Sparkline` |
| Tabular digits: changing prices never shift neighbouring content | `tabularNumbers()` |
| All mapping off the main thread (`flowOn`), injectable dispatchers for tests | repository, ViewModel |

State preservation: ViewModel survives rotation; tab, scroll positions and order-ticket text inputs use `rememberSaveable` / hoisted `LazyListState`; an *open ticket itself* survives process death via `SavedStateHandle` (see above); feed stops 5 s after backgrounding (`WhileSubscribed`).

Verify recomposition behaviour:
```
./gradlew :feature:market:assembleDebug -PcomposeCompilerReports=true
# reports in feature/market/build/compose_compiler
```
Then use Layout Inspector recomposition counts: only visible rows whose price changed should increment.

## Run

Requirements: JDK 17, Android Studio (latest stable) with the Kotlin Multiplatform plugin, Xcode 16+ for iOS.

```
gradle wrapper --gradle-version 8.14.3   # first time only: generates gradlew + wrapper jar
./gradlew :composeApp:installDebug        # Android
./gradlew allTests                        # common tests on JVM + iOS simulator (macOS)
```

**iOS:** create the Xcode project with the KMP wizard (kmp.jetbrains.com, "Share UI" template) or Android Studio's KMP template, then replace its Swift sources with `iosApp/iosApp/*.swift`. The build phase runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.

**SPM distribution:** `./gradlew :composeApp:assembleComposeAppReleaseXCFramework`, zip, checksum, publish; see `spm/Package.swift`.

## Testing
- `core/domain`: `FixedPointTest` (grouping, parsing without rounding, drift-free sums), `TradeValidatorTest` (every rule), `RetryPolicyTest` (retries transient failures, gives up after max attempts, never retries a non-transient error, cancellation always propagates).
- `core/data`: `DefaultMarketRepositoryTest`: conflation drops frames, never data (virtual time). `MockOrderRepositoryTest`: same idempotency key never creates a second order, different keys do, a permanently-down endpoint fails as expected. `MockResearchRepositoryTest`: succeeds and maps every DTO when nothing fails, exhausts retries and throws when the endpoint never recovers.
- `core/mvi`: `BaseViewModelTest`: `setState`/`sendEffect` on a throwaway ViewModel, plus that `SavedStateHandle` fields actually survive across ViewModel instances (the process-death case).
- `feature/market`: `MarketViewModelTest`: rejected order never hits the network; accepted order closes the ticket and emits a one-shot effect. `MarketScreenTest`: a real Compose UI test (`runComposeUiTest`) - tapping a filter chip and a retry button on the actual rendered composable, not just calling ViewModel methods directly.

Run everything: `./gradlew detekt test`

## Git workflow
Trunk-based: short-lived branches (`feat/`, `fix/`, `docs/`, `build/`), Conventional Commits, squash-free merges to keep a readable history, CI on every PR (`.github/workflows/ci.yml`).

## Next steps (out of scope for the exercise)
Real WebSocket feed with snapshot + delta and gap recovery · Koin or kotlin-inject once the graph grows · Baseline Profiles and Macrobenchmark on Android · SKIE for Swift Flow/sealed interop · Paparazzi / Roborazzi screenshot tests · secure storage + biometric step-up before order submission · accessibility pass (TalkBack labels on the tick-flash/sparkline, dynamic type scaling in `QuoteRowLayout`'s fixed columns) · `SavedStateHandle` restoration currently covers the order ticket only, not the whole `MarketUiState` - worth revisiting if more state becomes worth surviving process death.

> Prices and research content are mock data for demonstration only.
