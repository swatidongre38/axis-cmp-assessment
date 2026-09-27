package com.axis.marketinsights.feature.market

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.axis.marketinsights.designsystem.AxisTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The only Compose-level test in this module - everything else (MarketViewModelTest,
 * DefaultMarketRepositoryTest, TradeValidatorTest) tests logic below the UI layer. This one
 * exists specifically to prove the UI wiring itself - tapping a real composable produces the
 * intent the ViewModel expects - not just that the ViewModel's own methods work correctly.
 *
 * MarketScreen is a plain stateless composable (state/quotes/onIntent all passed in), so it
 * can be driven directly here without a real MarketViewModel, a fake MarketRepository, or any
 * coroutine setup at all.
 */
@OptIn(ExperimentalTestApi::class)
class MarketScreenTest {

    @Test
    fun tappingTheBondsFilterChip_sendsFilterChangedIntent() = runComposeUiTest {
        val receivedIntents = mutableListOf<MarketIntent>()
        val state = MarketUiState(
            filter = AssetFilter.All,
            research = ResearchState.Loaded(persistentListOf()),
        )

        setContent {
            AxisTheme {
                MarketScreen(
                    state = state,
                    quotes = { QuotesUiState() },
                    onIntent = { receivedIntents += it },
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        onNodeWithText("Bonds").performClick()

        assertEquals(listOf(MarketIntent.FilterChanged(AssetFilter.Bonds)), receivedIntents)
    }

    @Test
    fun researchLoadFailure_showsRetryButton_andRetryingSendsIntent() = runComposeUiTest {
        val receivedIntents = mutableListOf<MarketIntent>()
        val state = MarketUiState(
            filter = AssetFilter.All,
            research = ResearchState.Failed("Couldn't load research calls. Check your connection and retry."),
        )

        setContent {
            AxisTheme {
                MarketScreen(
                    state = state,
                    quotes = { QuotesUiState() },
                    onIntent = { receivedIntents += it },
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        // On phone-width layouts research sits behind the second tab.
        onNodeWithText("Research").performClick()
        onNodeWithText("Retry").performClick()

        assertEquals(listOf(MarketIntent.RetryResearch), receivedIntents)
    }
}
