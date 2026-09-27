package com.axis.marketinsights

import androidx.compose.runtime.Composable
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.axis.marketinsights.designsystem.AxisTheme
import com.axis.marketinsights.feature.market.MarketRoute
import com.axis.marketinsights.feature.market.MarketViewModel

@Composable
fun App() {
    AxisTheme {
        // createSavedStateHandle() gives MarketViewModel a real SavedStateHandle tied to this
        // screen's saved-instance-state, so an open order ticket can survive process death,
        // not just rotation. Falls out of the standard viewModelFactory/initializer pattern -
        // no manual wiring needed beyond this.
        val viewModel = viewModel<MarketViewModel>(
            factory = viewModelFactory {
                initializer { AppGraph.marketViewModel(savedStateHandle = createSavedStateHandle()) }
            },
        )
        MarketRoute(viewModel = viewModel)
    }
}
