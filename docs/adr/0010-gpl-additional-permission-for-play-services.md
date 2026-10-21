# 0010 — GPL-3.0 with an additional permission for Google Play services and Firebase

## Context

Pingogram is GPL-3.0-or-later (`REQUIREMENTS.md` §2). The `gms` flavor receives notifications through Firebase Cloud
Messaging (`docs/adr/0004-push-delivery.md`), which brings Google's Play services and Firebase client libraries. Some
of them are proprietary (POM license "Android Software Development Kit License"). Conveying a GPL-3.0 program combined
with them is not allowed without a permission from Pingogram's copyright holders, so a `gms` build could not be
distributed as things stood. From 0.3.0, every GitHub release ships a `gms` build next to the `foss` one.

The owner weighed two ways out on 2026-10-21:

- **Apache-2.0.** Permissive: the problem goes away, but anyone may also ship closed forks of Pingogram. Telegram
  clients are cloned and republished often, sometimes with malware or ads, and copyleft keeps such forks' source open.
- **GPL-3.0-or-later with an additional permission under section 7**, scoped to those libraries, following the FSF's
  template for linking with a non-free library (https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs).

## Decision

Keep GPL-3.0-or-later and add the additional permission in `LICENSE-EXCEPTION.md`: Pingogram may be combined with the
libraries of the Maven groups `com.google.android.gms` and `com.google.firebase` (and what they depend on under the same
terms), and the result conveyed. `LICENSE` stays the verbatim GPL-3.0 text, so tools and GitHub keep recognizing it.
Contributions are accepted under the GPL with this permission (`CONTRIBUTING.md`).

## Consequences

- `gms` builds can be distributed: GitHub releases attach them (`docs/RELEASE.md`), and Play later.
- Pingogram's own code stays copyleft; forks must publish their source, and may drop the permission (section 7).
- `foss` is unaffected: the build refuses Firebase and Play services there (`check<Variant>GoogleFree`), so F-Droid's
  rules are met without the permission.
- The permission names its libraries. Another proprietary library would need its own permission, or not to be used.
- This is the owner's decision as copyright holder, not legal advice; code from other contributors needs their consent,
  which the contribution terms now state.
