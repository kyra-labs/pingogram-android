# Translating Pingogram

Pingogram is translated on [Hosted Weblate](https://hosted.weblate.org/engage/pingogram/) (owner decision 2026-10-22,
`docs/adr/0018-translations-on-weblate.md`). Translators work in Weblate's web editor; Weblate sends the translations to
this repository as pull requests. Nobody edits `res/values-<language>/strings.xml` by hand, and nothing is
machine-translated into the repository.

## For translators

- Translate on Weblate, not in pull requests. Sign in, pick a language, start with the **Settings** and **Chats**
  components (what everyone sees first), then the others.
- Keep every placeholder as it is: `%1$s`, `%2$d` and the like are names, counts and dates that the app fills in. Their
  numbers say which is which, so a sentence can put them in any order.
- Plurals: fill in every form Weblate asks for (Arabic has six, Polish four); the app picks the right one.
- The comments above strings say where they show and what a placeholder holds. Ask in the string's comment thread on
  Weblate when something is unclear.
- Keep the app's tone: plain, friendly, short. "Telegram" stays as it is; "Pingogram" too, and is not translated.
- The glossary component holds the agreed words (Saved Messages, secret chat, chat folder…); use them.

## When a language ships

A language ships in the next build once it covers at least **80%** of the app's strings and plurals, all modules
together; strings it lacks show in English. The build decides it from the files themselves
(`pingogram.android.locales`, `build-logic`):

- `./gradlew :app:translationCoverage` prints each language's share and whether it ships.
- The languages that ship are generated into `xml/locales_config.xml` (Android 13+'s app languages and Settings →
  Language on every version) and into the resource filters, so a language below 80% is not packaged at all.
- Android Lint's `MissingTranslation` is informational: a shipped language may lack up to a fifth of the strings.
  `ExtraTranslation` (a string Pingogram no longer has) stays an error, so stale strings are removed.

## For developers

Every user-visible text is a string resource in its module's `res/values/strings.xml`, ready for translation:

- **No concatenation.** Build sentences from one string with placeholders, never from pieces joined in code; parts
  of a screen reader's description are joined with `core:ui`'s `spokenList` (`list_separator`, which languages
  translate).
- **Positional placeholders** (`%1$s`, `%2$d`), even when there is one, so translations can reorder them.
- **Plurals** (`<plurals>`) for every count, with `one` and `other` in English; never `if (count == 1)`.
- **Translator comments** (`<!-- … -->` above a string or a group) where a string is ambiguous: where it shows, what
  each placeholder holds, how long it may be.
- **`translatable="false"`** for the brand (Pingogram) and technical strings (URLs, the `t.me/` prefix); a format of
  punctuation only stays translatable, as punctuation differs between languages.
- Dates, times and numbers are formatted with the user's locale (`DateTimeFormatter`, `NumberFormat`), not in strings.
- User content inside a string is isolated (`bidiIsolated`, `docs/DESIGN_SYSTEM.md` §9) so it reads right in
  right-to-left languages.

Check layouts with the **pseudo-locales** debug builds include: in Android's developer options, add "English (XA)"
(accented, longer text, for German- and Finnish-length strings) or "Arabic (XB)" (right to left) as the system
language. The goldens of `app`'s `PseudoLocaleScreenshotTest` show key screens in both.

## Weblate setup (the owner)

One Weblate project, **Pingogram** (slug `pingogram`), with one component per module and a glossary. In the project:
**Settings → Access**: public; **Settings → Workflow**: translation review on, "Enable suggestions" on; **Settings →
Machine translation**: none configured (no machine translation reaches the repository).

The repository connection: connect the Hosted Weblate GitHub App to `kyra-labs/pingogram-android` ("Connect GitHub
account" on Hosted Weblate), or add the `weblate` user as a collaborator with write access. Then create the first
component, **Settings**, "From version control", with these values (the others are the same but for the name, slug and
paths, and choose "Use existing repository: pingogram/settings" so they share one checkout and one pull request):

| Field | Value |
|---|---|
| Component name / slug | `Settings` / `settings` |
| Version control system | GitHub pull request (via the GitHub App: "GitHub (via Weblate GitHub app)") |
| Source code repository | `https://github.com/kyra-labs/pingogram-android.git` |
| Repository branch | `main` |
| Push branch | `weblate` (Weblate pushes to this branch of the repository and opens a pull request from it into `main`) |
| Repository push URL | empty (the GitHub App pushes), or `git@github.com:kyra-labs/pingogram-android.git` with the `weblate` user |
| File format | Android String Resource |
| File mask | `feature/settings/src/main/res/values-*/strings.xml` |
| Monolingual base language file | `feature/settings/src/main/res/values/strings.xml` |
| Template for new translations | `feature/settings/src/main/res/values/strings.xml` |
| Edit base file | off |
| Language code style | Android style (`values-pt-rBR`) |
| Source language | English |
| Translation license | GNU General Public License v3.0 or later (`GPL-3.0-or-later`) |
| Contributor license agreement | "Translations are contributed under the project's license (GPL-3.0-or-later) and signed off under the Developer Certificate of Origin (https://developercertificate.org/)." |
| Push on commit | on |
| Commit message when translating | `chore(i18n): translate {{ component_name }} into {{ language_name }}`, a blank line, then `Translation: {{ project_name }}/{{ component_name }}`, `Translate-URL: {{ url }}` and `Signed-off-by: {{ author }}` |
| Version control parameters | Merge method `squash` (`merge_request_merge_method`); open a pull request on (`create_merge_request`) |

The components (name, slug, the module directory the paths start with):

| Component | Slug | Module directory |
|---|---|---|
| App | `app` | `app/src/main/res` |
| App (GitHub builds) | `app-foss` | `app/src/foss/res` |
| Common | `core-ui` | `core/ui/src/main/res` |
| Notifications | `notifications` | `core/notifications/src/main/res` |
| Sign-in | `auth` | `feature/auth/src/main/res` |
| Chat | `chat` | `feature/chat/src/main/res` |
| Chats | `chats` | `feature/chats/src/main/res` |
| Contacts | `contacts` | `feature/contacts/src/main/res` |
| Media | `media` | `feature/media/src/main/res` |
| Profiles | `profile` | `feature/profile/src/main/res` |
| QR codes | `scanner` | `feature/scanner/src/main/res` |
| Settings | `settings` | `feature/settings/src/main/res` |

Plus a **Glossary** component (Weblate's own glossary, "Manage → Glossary"), shared by the components.

Add-ons on the project (Manage → Add-ons): **Squash Git commits**, mode "All commits into one", commit message
`chore(i18n): translations from Weblate`, "Append trailers" on (it credits every translator with `Co-authored-by:`);
**Cleanup translation files** (drops strings the source no longer has). The pull request from `weblate` is reviewed and
squash-merged like any other: CI must pass (placeholders and plurals are checked by Lint), and its title stays
`chore(i18n): translations from Weblate`.

The README's badge links to `https://hosted.weblate.org/engage/pingogram/` and shows
`https://hosted.weblate.org/widget/pingogram/svg-badge.svg`; check both work once the project exists.
