# ADR-001 Summary: Market Insights & Execution Architecture

**Status:** Proposed  
**Author:** Swati Kaknale  
**Audience:** Product, UX, Backend Architecture
**Full technical version:** [ADR-001-market-insights-architecture.md](./ADR-001-market-insights-architecture.md)

## What we're building

One module, shared across Android and iOS, showing a live bond/equity price feed
alongside analyst research calls and a trade ticket - built once instead of twice.

## The decision

**Compose Multiplatform + Kotlin Multiplatform**, with the business logic (pricing rules,
trade validation, data access) in modules that have zero dependency on the UI layer. That
separation is enforced by the project's build configuration, not just team discipline - a
UI change literally cannot reach into trading logic by accident.

**State is split into two speeds.** Prices update up to ~500 times a second; the screen is
refreshed about 20 times a second, which is as fast as a person can actually read. Nothing
is lost - every price update is recorded - only how often the screen is told about it is
throttled. Everything else (filters, the order form) updates only when the user acts on it.

**Money is never a floating-point number.** Prices are stored as exact whole-number values,
so there's no rounding drift and every price check happens against the real number, not an
approximation.

## Why this fits a trading application specifically

A live feed that stutters or lags looks broken to a trader, especially in the first
volatile minutes after markets open. A price that's off by a fraction of a paisa due to
rounding is a real financial and compliance problem, not a cosmetic one. And if an order
submission times out and gets retried, it must never create a second, duplicate order - our
retry logic is built specifically so that can't happen.

## Business value

| Decision | What it means for the business |
|---|---|
| One codebase, two platforms | Features ship on Android and iOS together, one team, one backlog |
| Shared trade-validation rules | Compliance rules can't drift between platforms - changed once, tested once |
| Exact (not floating-point) pricing | No reconciliation mismatches with the exchange or back office |
| Smooth, throttled live feed | Reliable on mid-range devices during volatile market opens, without excess battery/data use |
| Safe order retries | A flaky connection during a trade never risks placing a duplicate order |

## Trade-offs, briefly

Compose Multiplatform on iOS is younger than SwiftUI, particularly for accessibility and
text input polish - any individual screen can be rebuilt natively while still calling the
same shared logic if that becomes necessary. The team will need to build comfort with
Kotlin coroutines and some iOS-specific memory-management behavior; this is being handled
through code review rather than assumed away.

*Full architecture detail - diagrams, the API/retry contract, and alternatives considered -
is in the linked technical version of this document.*
