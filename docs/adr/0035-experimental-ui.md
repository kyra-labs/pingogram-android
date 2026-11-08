# 0035 · Experimental UI

Date: 2026-11-07 · Status: accepted by the owner on 2026-11-07 (the brief of that day); the switch was retired on
2026-11-08, when the owner made the experimental look Pingogram's only look (see "Retired" below). Points marked "Owner decision to
confirm (2026-11-07)" here and in `docs/DESIGN_SYSTEM.md` §13 are Pingogram's reading where the owner's design shows
something the owner has not decided.

## Context

The owner wants to try a bolder Material 3 Expressive look without replacing the current one: wide display titles,
expressive shapes on avatars and icons, vivid fills, a coloured header and account card, and chat bubbles in each
sender's hue. The design is a canvas of mock-ups for the chat list, search, a group chat, a channel, Contacts and
Settings, in dark and light. It uses Roboto Flex as a stand-in font and literal colors and pixel sizes; the app maps
them to its tokens (color roles, the shape scale, `MaterialShapes`, `spacing` and `sizes`, the type scale, Google Sans
Flex), never copying a hex value or a pixel size into feature code.

The current look stays the default. Another stream of work keeps changing it (the pinned group, the bottom fade, the
navigation bar's labels, the round New chat button, the tabs' large header, the folder slide), and those decisions
are for the standard look.

## Decision

An **opt-in switch**, "Experimental UI", off by default, and **one UI with style variants**, never a copy of a screen.

- **Storage**: `core:domain`'s `ExperimentalUiRepository`, `DataStoreExperimentalUiRepository` in `core:datastore`
  (the settings file, `appearance.experimental_ui`), kept on this device.
- **Delivery**: `MainActivity` reads it with the Appearance settings and the other settings it waits for before the
  first frame (the same file, so waiting costs nothing), and provides it as `core:ui`'s `LocalExperimentalUi`, a static
  composition local set once per activity. The app never draws one look and then the other.
- **Switching**: turning it on or off recomposes the app in place, as "Hide phone numbers" does: a static local that
  changes invalidates every composable that reads anything below it. That is cheaper than recreating the activity
  (which would read the settings again behind no splash, create every view model and screen again and lose nothing
  more than a recomposition does) and than a cross-fade (which would hold two compositions of the whole app at once
  for its duration). The change happens on Settings home, where the user is looking at the switch; the view models,
  scroll positions and saved state stay.
- **Where it shows**: Settings home. In the standard look a switch row "Experimental UI" with a "Beta" label, in a
  group of its own before "Log out" (Owner decision to confirm (2026-11-07): the brief names the row, not its place);
  in the experimental look the design's labs card under the account card.
- **How screens branch**: a composable that looks different reads `LocalExperimentalUi` and picks its style at the
  smallest scope that differs (a color, a shape, a slot), keeping its state, events, view model, keys, content types
  and accessibility as they are. Shared experimental parts live in `core:ui` and read the local themselves. No screen
  is duplicated; a view model never reads the local.
- **What it covers**: the chat list, global search, the conversation (top bar, pinned bar, composer, jump buttons,
  group bubbles, channel posts), Contacts and Settings home; settings pages keep their look. Each lands in its own
  pull request, in that order, each listing the owner questions it raises.

## Consequences

- **Two looks to keep in goldens**: every experimental screen state gets goldens in light, dark, AMOLED black, high
  contrast, right to left and 200% font beside the standard ones, which do not change.
- **Performance**: the branch is one read of a static local per composable that differs; the chat list and the
  conversation keep their recomposition and scroll-cost tests in both looks.
- **Retiring it**: once the owner picks one look, the switch, the local and the losing look's branches are deleted,
  and this ADR records it.
- **Not a theme**: the experimental look uses the user's color theme roles; a "Coral" basic color theme shows the
  design as drawn (a separate, owner-confirmed choice in `docs/DESIGN_SYSTEM.md` §13).

## Retired (2026-11-08)

The owner picked the experimental look: it is Pingogram's only look, with no switch. One pull request removes the switch
(the Settings row and the labs card, `ExperimentalUiRepository` and its stored key, which is no longer read) and makes
`LocalExperimentalUi` true everywhere, recording every golden in that look. The standard look's branches then leave
module by module, and the local with the last of them; `docs/DESIGN_SYSTEM.md` §13 is the spec where it and §1–§12
differ. Done the same day: the contacts, chat list, conversation and Settings modules lost their other look, then
`core:ui` and `:app`, and `LocalExperimentalUi` with them. A few names keep "experimental" (`experimentalTypography`,
`sizes.experimental*`) as tokens, not as a look.
