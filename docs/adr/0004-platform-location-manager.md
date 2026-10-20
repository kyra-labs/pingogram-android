# 0004 — Sharing the location: the platform's `LocationManager`, foreground only, no map

Date: 2026-10-20 · Status: accepted

## Context

The attach menu sends the device's location (`REQUIREMENTS.md` FR-C5, "location"): TDLib's `inputMessageLocation`
with a latitude, a longitude and a horizontal accuracy of 0–1500 m (td_api.tl of TDLib 1.8.68; live locations are a
separate `inputMessageLiveLocation`). Finding the location needs a location API and the location permissions, which
the owner approved for the foreground only (`ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`, no
`ACCESS_BACKGROUND_LOCATION`).

Options for finding it:

- **Google Play services' fused location provider** (`play-services-location`): the best accuracy and battery on
  Google phones, but a Google Play services dependency, which the `foss` flavor must not have (`docs/ARCHITECTURE.md`,
  flavors), about 1 MB with its dependencies, and nothing that GMS-free devices can run. It could be added to the
  `gms` flavor only, behind an interface; that is two implementations for one sheet.
- **The platform's `LocationManager`** through androidx.core's `LocationManagerCompat`, already a dependency:
  `getCurrentLocation` (one fresh measurement; Android 11+ natively, emulated before) or `requestLocationUpdates`
  (Android 12's `LocationRequest`, the listener API before). Providers: GPS, the network provider (where the device
  has one: on Google phones Play services backs it, on GMS-free ROMs it may be missing or come from microG), and from
  Android 12 the platform's `fused` provider, which combines them and needs no Google library.

Options for showing it:

- **A map** needs a map SDK (Google Maps, Mapbox, MapLibre with OSM tiles) and tiles from a server: a third-party
  network service (`REQUIREMENTS.md` §6.2: network only to Telegram and links the user opens) and several MB.
- **A static map image** (Telegram's `getMapThumbnailFile` downloads one through Telegram) would stay within the
  network rule, but it is a separate feature for messages too and not needed to send a location.
- **Coordinates and accuracy** with "Open in maps" (a `geo:` link to the user's maps app).

## Decision

- `AndroidDeviceLocationRepository` (`core:data`, behind `core:domain`'s `DeviceLocationRepository`) uses the platform's
  `LocationManager` in both flavors: with precise access GPS and the network provider, with approximate access the
  network provider only (GPS needs precise access), and the `fused` provider from Android 12 on, where present.
- Continuous updates (`requestLocationUpdates`, every second, high accuracy) while the sheet is open and the screen
  collects it, rather than `getCurrentLocation`: the sheet shows the last known location (at most 2 minutes old) at
  once and the accuracy improving, as the user decides when to send. The search stops when the sheet closes, five
  seconds after the app goes to the background, and when no first location comes within 30 s (the sheet then offers
  "Try again"). A newer measurement replaces the shown one only when it is at least as accurate or more than 10 s newer.
- Both permissions are asked together after the sheet says why, so Android 12+ offers "Approximate"; an approximate
  location is sent as it is (Telegram takes accuracy up to 1.5 km), and the sheet offers to ask for the precise one.
- No map: the sheet shows the coordinates, their accuracy, and "Open in maps" where an app on the device handles
  `geo:` links (the app's manifest declares that `<queries>` intent, so Android 11+ answers the check).
- No live location: it would need background location or a foreground service for its whole period.

## Consequences

- No new dependency, no Google Play services in `foss`, nothing that talks to a server but TDLib.
- On GMS-free devices without a network provider, approximate access finds nothing before Android 12 (no `fused`
  provider), and precise access relies on GPS, which can take up to the 30 s timeout indoors. The sheet says so and
  offers "Try again".
- On Google phones the platform's network and fused providers are backed by Play services anyway, so accuracy is
  close to the fused provider's.
- Sending a map preview, a venue search (`getInlineQueryResults` of a venue bot) or live locations remain possible
  later; each needs its own decision.
