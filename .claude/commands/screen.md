Scaffold a new screen called $ARGUMENTS following our conventions.

Create, in the right feature module:
- `<Name>Route` (hoists the Hilt ViewModel, collects state, handles effects)
- `<Name>Screen(state, onEvent, modifier)` — stateless, M3 Expressive components only
- `<Name>UiState` (sealed: Loading, Empty, Error, Content), `<Name>Event`, `<Name>Effect`
- `<Name>ViewModel` with SavedStateHandle args
- `@Preview`s for every state in light + dark + dynamic color
- Type-safe route object in core:navigation and NavHost wiring
- Unit test for the ViewModel using FakeTdlibClient
- Roborazzi screenshot test

Then run `./gradlew check` and report.
