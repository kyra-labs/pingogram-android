# 0012 — QR code scanner: CameraX and ZXing, on the device

Status: accepted (camera permission and CameraX approved by the owner, 2026-10-21)

## Context

`REQUIREMENTS.md` FR-P5 asks for joining by QR code, and Telegram's desktop apps log in by a QR code that a phone
already logged in scans ("Link desktop device", `confirmQrCodeAuthentication`). People also share their profile as a QR
code. Pingogram had no camera code: it shows QR codes (QR login, a chat's link) with ZXing's encoder, but reads none.

Reading QR codes needs a camera pipeline and a decoder. The options:

- **Google's code scanner (ML Kit `play-services-code-scanner`)**: no camera permission (Play services shows its own
  scanner UI), but it is Google Play services. The `foss` flavor must stay free of them (`check<Variant>GoogleFree`,
  `docs/adr/0004-push-delivery.md`), and two scanners per flavor would split the code and the tests.
- **ML Kit's bundled barcode model (`com.google.mlkit:barcode-scanning`)**: no Play services at run time, but a
  closed-source model and native code bundled into the app under Google's ML Kit terms, which the F-Droid build and the
  GPL do not take.
- **ZXing core + a camera API**: ZXing's core (Apache 2.0, pure Java) is already in the app for the encoder; its
  `QRCodeReader` reads codes from a luminance plane. For the camera: the platform's Camera2 (a lot of device-specific
  code), or **CameraX** (Jetpack, Apache 2.0, first party, no network, no Play services), whose `ImageAnalysis` hands
  frames to an analyzer on a background executor and whose `camera-compose` draws the preview as a composable
  (`CameraXViewfinder`), so no `PreviewView` (a View) is needed (CLAUDE.md rule 4).

## Decision

- **CameraX 1.6.2** (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose`) in `:feature:scanner` only,
  and **ZXing core 3.5.4** for decoding. No Play services, so `foss` stays Google-free; both flavors scan the same way.
- **Pipeline** (`ScannerCamera`, `QrAnalyzer`, `QrDecoder`): a `Preview` into `CameraXViewfinder`, and an
  `ImageAnalysis` of about 720p frames (`STRATEGY_KEEP_ONLY_LATEST`) on a single background thread. At most five frames
  a second are read: the Y plane goes through ZXing's `PlanarYUVLuminanceSource`, `HybridBinarizer` and `QRCodeReader`
  (QR codes only, UTF-8, inverted codes too); other frames are closed at once. The first text found stops the analysis
  until the user scans again. The camera is bound to the screen's lifecycle and runs only while the scanner shows; the
  torch follows its toggle where the camera has one.
- **Scan from an image**: the Photo Picker (no permission), the image decoded at most 1600 px on its longer side on the
  IO dispatcher, then ZXing's `RGBLuminanceSource`. It is also all a device without a camera has.
- **Permission**: `CAMERA`, the scanner's only use, asked when the scanner opens, after a rationale on the screen; a
  refusal for good offers the app's settings. `android.hardware.camera.any`, `.camera` and `.camera.autofocus` are
  declared optional, so devices without a camera install Pingogram (pinned by `AppManifestTest`).
- **Privacy**: frames are read on the device and dropped; nothing the camera sees is kept, logged or sent. A code's text
  goes to TDLib only when it is a Telegram link (`isTelegramLink`), through `LinkRepository.resolve`, which only reads
  (`docs/adr/0013-deep-links.md`); any other text stays on the screen, to copy.
- **What a code opens**: the target's screen, where anything that changes something asks first: the join sheet, the
  confirmation of a device's login (also for `tg://login` links from other apps). Opened from "Link desktop device",
  the scanner opens only login codes and explains other codes.

## Consequences

- Size: the CameraX AARs are about 4.3 MB before R8 (`camera-core` 1.2 MB, `camera-camera2` 1.2 MB and the
  `camera-camera2-pipe` it brings 1.6 MB, `viewfinder-core`/`-compose` 0.16 MB, `camera-compose` and `camera-lifecycle`
  0.1 MB); ZXing core is 0.6 MB before R8, of which R8 keeps the encoder and the QR decoder. The release APK grows by
  the code R8 keeps of them (see the PR for the measured size). No native code.
- A camera that another app holds, or a policy that forbids it, shows as "no camera": scanning from an image still works.
- Other barcode formats, scanning in the conversation's attach menu and a scanner opened from the chat list are not
  built; adding them changes only where `navigateToQrScanner` is called.
