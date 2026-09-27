# PR review: ResearchViewModel (fetch research calls)

```kotlin
class ResearchViewModel {
    var researchCalls = mutableListOf<String>()
    fun fetchCalls() {
        GlobalScope.launch {
            val result = api.getCalls() // suspending function
            researchCalls = result
        }
    }
}
```

**Line 4 - GlobalScope**
Blocking. This isn't tied to the screen's lifecycle so it never gets cancelled - leaves the
call running (and this ViewModel referenced) even after the user navigates away. It's
marked `@DelicateCoroutinesApi` for exactly this reason, worth reading why next time it
shows up in autocomplete. Extend the multiplatform `ViewModel` and use `viewModelScope`
instead, it's cancelled for you in `onCleared()`.

**Line 2/6 - `var researchCalls`**
This won't actually update the screen. Reassigning a var doesn't notify Compose of
anything - you need a `MutableStateFlow` that the UI collects, or nothing redraws even
after the network call succeeds. Also make it private, a public mutable list means anyone
can mutate your state from outside.

Related: once this is a StateFlow, the thread-safety issue in the current version (write on
`Dispatchers.Default`, read on main) goes away for free.

**No loading/error state**
Right now the UI can't tell "loading" from "empty" from "failed." For research calls that's
not just annoying, a failed load silently looking like "no calls today" is actually
misleading in a trading context. Wrap this in a sealed `ResearchUiState` (Loading /
Success / Error).

**Where does `api` come from?**
Take a `ResearchRepository` in the constructor instead of reaching out to some global. Makes
this testable with a fake and decouples the ViewModel from whatever `api` actually is.

**nit:** `List<String>` throws away rating/target/analyst - use `ResearchCall` once the
repository's returning real domain objects. And prefer `ImmutableList` over `List` for
whatever ends up in state, Compose treats it as stable and can skip recomposing rows that
didn't change.

**One more thing to watch once you add try/catch here** - catching `Exception` broadly also
swallows `CancellationException`, which quietly breaks structured concurrency. Either
rethrow it or use `suspendRunCatching` from `core:domain/util`.

Roughly what I'd land on:

```kotlin
class ResearchViewModel(
    private val repository: ResearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResearchUiState>(ResearchUiState.Loading)
    val uiState: StateFlow<ResearchUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init { fetchCalls() }

    fun fetchCalls() {
        fetchJob?.cancel() // otherwise a second call while one's in flight can race it
        fetchJob = viewModelScope.launch {
            _uiState.value = ResearchUiState.Loading
            _uiState.value = suspendRunCatching { repository.getResearchCalls() }.fold(
                onSuccess = { ResearchUiState.Success(it.toImmutableList()) },
                onFailure = { ResearchUiState.Error("Couldn't load research calls. Pull to retry.") },
            )
        }
    }
}
```

Ping me if this ever needs to become a live stream instead of a one-shot fetch, different
shape (`stateIn` + `WhileSubscribed`), happy to pair on it rather than write it blind here.

Two smaller KMP things since this is your first PR touching commonMain: no `java.*`/
`android.*` in here, use `expect/actual` if you need something platform-specific. And
`StateFlow` doesn't reach Swift automatically - we're using SKIE for that, ask if the iOS
side of this comes up.
