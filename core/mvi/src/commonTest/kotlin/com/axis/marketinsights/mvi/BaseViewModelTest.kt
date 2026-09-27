package com.axis.marketinsights.mvi

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class CounterState(val count: Int = 0)
private sealed interface CounterIntent {
    data object Increment : CounterIntent
    data class SaveNote(val text: String) : CounterIntent
}
private sealed interface CounterEffect { data class ReachedTen(val value: Int) : CounterEffect }

private class CounterViewModel(
    savedStateHandle: SavedStateHandle? = null,
) : BaseViewModel<CounterState, CounterIntent, CounterEffect>(CounterState(), savedStateHandle) {
    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> {
                setState { copy(count = count + 1) }
                if (currentState.count == 10) sendEffect(CounterEffect.ReachedTen(currentState.count))
            }
            is CounterIntent.SaveNote -> saveField("note", intent.text)
        }
    }

    fun restoredNote(): String? = restoreField("note")
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun setStateUpdatesTheExposedStateFlow() = runTest(dispatcher) {
        val viewModel = CounterViewModel()
        repeat(3) { viewModel.onIntent(CounterIntent.Increment) }
        assertEquals(3, viewModel.state.value.count)
    }

    @Test
    fun sendEffectDeliversExactlyOnce() = runTest(dispatcher) {
        val viewModel = CounterViewModel()
        val received = mutableListOf<CounterEffect>()
        backgroundScope.launch { viewModel.effects.collect { received += it } }

        repeat(10) { viewModel.onIntent(CounterIntent.Increment) }
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(10, (received.single() as CounterEffect.ReachedTen).value)
    }

    @Test
    fun withoutASavedStateHandle_saveFieldIsANoOp() = runTest(dispatcher) {
        // No SavedStateHandle passed - this is the normal case in a unit test, and saveField
        // should not throw just because there's nowhere to actually persist to.
        val viewModel = CounterViewModel(savedStateHandle = null)
        viewModel.onIntent(CounterIntent.SaveNote("hello"))
        assertNull(viewModel.restoredNote())
    }

    @Test
    fun withASavedStateHandle_fieldsSurviveAcrossViewModelInstances() = runTest(dispatcher) {
        // Same SavedStateHandle instance handed to two different ViewModel instances -
        // simulates the OS killing and recreating the ViewModel after process death.
        val handle = SavedStateHandle()
        val first = CounterViewModel(handle)
        first.onIntent(CounterIntent.SaveNote("draft order"))

        val recreated = CounterViewModel(handle)
        assertEquals("draft order", recreated.restoredNote())
    }
}
