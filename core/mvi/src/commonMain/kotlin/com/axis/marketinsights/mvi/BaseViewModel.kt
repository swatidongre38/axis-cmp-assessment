package com.axis.marketinsights.mvi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Generic MVI scaffolding: every screen has one immutable [State], reacts to [Intent]s, and
 * can fire one-off [Effect]s (snackbars, navigation) that should never replay on rotation.
 * Pulling this into its own module means every new feature gets the same shape for free -
 * a ViewModel just declares its State/Intent/Effect types and implements [onIntent].
 *
 * [savedStateHandle], when provided, survives PROCESS DEATH (the OS killing the app in the
 * background on low memory), which plain in-memory StateFlow does not - a rotation alone is
 * already handled by ViewModel scoping, but process death is not. This matters most for
 * screens with something the user would be upset to lose, like a half-filled order ticket.
 *
 * Deliberately NOT automatic: State objects here are often not trivially saveable (they hold
 * ImmutableList, custom types, etc.), so this class does not try to serialize the whole
 * State automatically. Instead it gives subclasses [saveField]/[restoreField] to persist just
 * the specific primitive values that matter for restoring state, and the subclass decides how
 * to reconstruct the rest (usually by re-deriving it from already-available data, like looking
 * up a live Quote by an instrument id that was saved).
 */
abstract class BaseViewModel<State : Any, Intent : Any, Effect : Any>(
    initialState: State,
    protected val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    /** Current state, read synchronously - handy inside onIntent without collecting. */
    protected val currentState: State get() = _state.value

    /** Every subclass must handle its own intents - there's no sensible default here. */
    abstract fun onIntent(intent: Intent)

    protected fun setState(reducer: State.() -> State) {
        _state.update(reducer)
    }

    /** Fire-and-forget from a non-suspend call site (e.g. straight out of onIntent). */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /** Use this instead when you're already inside a coroutine launched on viewModelScope. */
    protected suspend fun sendEffectNow(effect: Effect) {
        _effects.send(effect)
    }

    /**
     * Persists one primitive value under [key] so it survives process death. No-ops silently
     * if no SavedStateHandle was provided (e.g. in a unit test) - callers don't need to null-check.
     */
    protected fun <T : Any> saveField(key: String, value: T?) {
        if (value == null) savedStateHandle?.remove<T>(key) else savedStateHandle?.set(key, value)
    }

    protected fun <T> restoreField(key: String): T? = savedStateHandle?.get<T>(key)
}
