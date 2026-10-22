# 0020 — Translations on Hosted Weblate

Status: accepted (owner decision 2026-10-22: Hosted Weblate, its libre plan for open-source projects; self-hosting
stays possible; `docs/ROADMAP.md` M6 "Localization: at least 5 languages")

## Context

Pingogram ships in English only (its locale config listed English alone). Its 1,700 or so strings and plurals live in
one `res/values/strings.xml` per module (`:app` and its `foss` flavor, `core:ui`, `core:notifications` and every
feature), already ready for translation: no user-visible text in Kotlin outside previews, plurals for counts, positional
placeholders (`%1$s`), user content wrapped in bidirectional isolates where strings are built (`docs/DESIGN_SYSTEM.md`
§9), comments for translators above groups of strings. Until this decision Android Lint's `MissingTranslation` was
fatal, so a language could only ship complete, and adding one meant a line in a hand-kept `locales_config.xml`.

Volunteer translators need a web tool that shows each string with its comment and context, keeps translations in sync
with the source as strings change, and hands translations back as commits or pull requests. Two are common for open
source Android apps:

| | Weblate (Hosted Weblate) | Crowdin |
|---|---|---|
| Software | Free software (GPL-3.0-or-later), self-hostable | Proprietary SaaS |
| Free for Pingogram | "Libre" hosting for public projects under an OSI/FSF libre license, after approval (https://weblate.org/hosting/) | "Crowdin for open source", on request: OSI license, public source, no commercial product, project lead applying, an active community, regular builds (https://crowdin.com/page/open-source-project-setup-request) |
| Android `strings.xml` | Yes, plurals and comments included; one component per module | Yes, plurals and comments; one file mapping per module |
| Git integration | Pushes commits or a pull request to the repository; reads `strings.xml` from it | GitHub app opens a pull request of translations; reads sources from the repository |
| Machine translation | Optional, configurable per project | Its Global TM and machine translation; the open-source plan asks to contribute translations to its Global TM |
| Fit with Pingogram | Free software like Pingogram (GPL), no lock-in, the platform F-Droid itself translates on; no requirement to share translations with a vendor | Proprietary; and the free plan's conditions (three months of history, a current news section) need checking |

The terms above were read on 2026-10-22 from the linked pages; check them again before signing up.

## Decision

Hosted Weblate's Libre plan: Weblate is free software like Pingogram, F-Droid translates its own apps on it, and the
project can move to a self-hosted instance without losing anything. The repository stays the source of truth:

- One component per module's `res/values/strings.xml` (and `app/src/foss/res/values/strings.xml`), source language
  English, file format "Android String Resource", new translations to `res/values-<tag>/strings.xml`.
- Weblate pushes to the `weblate` branch and opens a pull request into `main`, squashed by its "Squash Git commits"
  add-on into `chore(i18n): translations from Weblate` with every translator as `Co-authored-by:`, and squash-merged
  after CI (Lint checks placeholders and plurals: `StringFormatInvalid`, `StringFormatCount`, `ExtraTranslation`).
- A language ships once it covers 80% of all modules' strings (owner decision 2026-10-22): the build computes it and
  generates the locale config and resource filters (`pingogram.android.locales`), so no list is kept by hand. Strings a
  shipped language lacks show in English, which is why Lint's `MissingTranslation` is informational rather than fatal
  (`REQUIREMENTS.md` §7 is updated).
- No machine translation into the repository; translators may use Weblate's suggestions only where they review them.
- `docs/TRANSLATING.md` holds the workflow for translators and developers and the exact Weblate settings.

## Consequences

- The owner creates the Weblate project and connects the repository (the Hosted Weblate GitHub App, or the `weblate`
  user with write access), and Hosted Weblate approves the Libre plan.
- Translations of Telegram's own texts (`getLanguagePackStrings`) stay out of scope (`REQUIREMENTS.md` §6.4).
