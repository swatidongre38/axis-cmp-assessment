package com.axis.marketinsights

import androidx.compose.ui.window.ComposeUIViewController
import com.axis.marketinsights.domain.order.TradeValidator
import platform.UIKit.UIViewController

/** Swift side calls this as MainViewControllerKt.MainViewController(). */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/** Lets native Swift screens reuse the exact same validator instance the Compose UI uses. */
fun sharedTradeValidator(): TradeValidator = AppGraph.tradeValidator
