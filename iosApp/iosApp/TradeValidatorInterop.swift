import Foundation
import ComposeApp

// Calling the shared KMP business logic (TradeValidator) directly from Swift.
//
// This matters because a native SwiftUI screen - say a widget or a watch companion later -
// can enforce exactly the same pre-trade rules as the Compose UI and Android, since it's
// running the same compiled Kotlin, covered by the same commonTest suite.
//
// A few interop notes worth remembering:
//  - a Kotlin `object`             -> SystemMarketClock.shared
//  - companion object members      -> RiskLimits.companion.standard, FixedPoint.companion.parse(...)
//  - enum entries                  -> lower camelCase in Swift: .buy, .limit
//  - sealed interface subtypes     -> flattened class names: ValidationResultRejected
//  - Kotlin Long / Int             -> Int64 / Int32
//  - List<T>                       -> [T]
//  - `suspend fun`                 -> Swift async throws (called on the main thread by default)
//  - Flow / StateFlow               -> not bridged out of the box; SKIE or KMP-NativeCoroutines
//    turn a StateFlow into something Swift can `for await` over.

@MainActor
final class NativeOrderValidator: ObservableObject {

    @Published private(set) var violations: [String] = []

    // Either build a fresh validator with the same rules...
    private let validator = TradeValidator(
        limits: RiskLimits.companion.standard,
        clock: SystemMarketClock.shared
    )
    // ...or reuse the exact instance the Compose screen is already using:
    // private let validator = MainViewControllerKt.sharedTradeValidator()

    /// Returns true if the order clears every pre-trade check.
    func validateLimitOrder(
        quote: Quote?,
        instrumentId: String,
        side: OrderSide,
        quantity: Int64,
        limitPriceText: String
    ) -> Bool {
        // Money never crosses the bridge as a Double - parse the text through the shared type.
        let scale = quote?.instrument.priceScale ?? 2
        let limitPrice = FixedPoint.companion.parse(text: limitPriceText, scale: scale)

        let order = OrderRequest(
            instrumentId: instrumentId,
            side: side,
            type: .limit,
            quantity: quantity,
            limitPrice: limitPrice
        )

        let result = validator.validate(order: order, quote: quote)

        if let rejected = result as? ValidationResultRejected {
            violations = rejected.violations.map { $0.message }
            // rule is an enum, so I can branch on it instead of matching message strings:
            if rejected.violations.contains(where: { $0.rule == .staleQuote }) {
                // e.g. trigger a quote refresh here
            }
            return false
        }
        violations = []
        return true
    }

    /// Calling a Kotlin suspend function with Swift concurrency.
    func submit(order: OrderRequest, quote: Quote?, placeOrder: PlaceOrderUseCase) async -> String {
        do {
            let result = try await placeOrder.invoke(order: order, quote: quote)
            switch result {
            case let accepted as PlaceOrderResultAccepted: return "Placed \(accepted.ack.orderId)"
            case let rejected as PlaceOrderResultRejected: return rejected.violations.first?.message ?? "Rejected"
            case let failed as PlaceOrderResultFailed:     return failed.reason
            default:                                       return "Unknown result"
            }
        } catch {
            // Only cancellation should ever surface here - the use case catches everything else.
            return "Cancelled"
        }
    }
}

// With SKIE (https://skie.touchlab.co) this gets nicer and exhaustive:
//
//   switch onEnum(of: validator.validate(order: order, quote: quote)) {
//   case .valid:            violations = []
//   case .rejected(let r):  violations = r.violations.map(\.message)
//   }
//
// and a StateFlow<QuotesUiState> becomes something you can `for await q in vm.quotes { ... }` over.
