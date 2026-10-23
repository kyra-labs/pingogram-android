# Telegram API terms: compliance checklist

`REQUIREMENTS.md` §2 and §9.5 ask for Telegram's API terms (https://core.telegram.org/api/terms) to be reviewed by
the owner and documented in the release notes. This is that review's checklist: each rule as the page states it
(read 2026-10-22), and where Pingogram meets it. Every GitHub release links here. Before 1.0, the owner reads the
current page again, checks each row and signs off at the bottom. Telegram sends changes to the terms as an in-app
notification to the developer's account.

| Rule | What it asks | How Pingogram meets it | Where |
|---|---|---|---|
| 1.1 Privacy | Guard users' privacy with utmost care; follow the security guidelines | TDLib does all of the protocol and its cryptography. Its database is encrypted with a Keystore-wrapped key, out of every backup. No analytics, ads or crash reporters. Cleartext traffic is refused. Logs stay on the device | `docs/TDLIB.md`, `REQUIREMENTS.md` §6.2, `PRIVACY.md` |
| 1.2 Extending | Features may be added if they break none of the terms | Additions (color themes, the widget, Send feedback) only change how things look, or stay on the device | — |
| 1.3 Basic features | Basic features work as expected with other Telegram users; never force others to install Pingogram to see content | Messages, media, calls and secret chats go through TDLib's standard API and interoperate with every client. Nothing is Pingogram-only | — |
| 1.4 No interference | Do not act for users without their consent, block self-destructing content, hide online status, alter read receipts or block typing status | No ghost mode, read-receipt or typing tricks. Online status is set only while Pingogram is visible (`TdlibNetworkTypeReporter`). Self-destruct timers are TDLib's. Nothing is sent without a tap (shares, bot buttons, "Start" all wait for the user) | `docs/PERFORMANCE.md` §3, `docs/adr/0014-direct-share.md` |
| 1.5 No AI training | Data from Telegram may not train or develop AI or ML models | Pingogram trains nothing and sends no data anywhere but Telegram (and Google's push service in `gms`) | — |
| 2.1 Own api_id | Obtain your own `api_id` | The owner's own, from my.telegram.org, never committed. It comes from `secrets.properties` and CI secrets | `docs/OWNER_SETUP.md`, `CLAUDE.md` rule 1 |
| 2.2 Transparency | Users must know the app uses the Telegram API: prominently in store descriptions and in the in-app intro | Welcome: "A fast, private Telegram client for Android", "It isn't made by Telegram". About: "an unofficial Telegram client built on TDLib". The store listing's first lines say so (`docs/PLAY_STORE.md` §4) | `feature:auth`, `feature:settings` |
| 2.3 Name | The title may not include "Telegram" unless preceded by "Unofficial" | The app is "Pingogram"; "Telegram" appears only in descriptions ("a Telegram client") | `CLAUDE.md` rule 2 |
| 2.4 Logo | Do not use the official Telegram logo | Pingogram's own artwork (`art/`) and Material Symbols only | `CLAUDE.md` rule 9 |
| 3.1–3.2 Monetization | Monetization is allowed and must be stated in every store description | Pingogram has none: no ads of its own, no purchases, no subscriptions. The store listing says it is free, without ads of its own | `docs/PLAY_STORE.md` §4 |
| 3.3 Sponsored messages | Apps that show channels must support official sponsored messages and not interfere with them | Shown where TDLib returns them (`getChatSponsoredMessages`), as a labelled card, viewed and clicked through TDLib, with "About this ad" and reporting. Never hidden or restyled | `REQUIREMENTS.md` §2, `docs/DESIGN_SYSTEM.md` §6 "Sponsored message" |
| 4 Breach | A notice to the app's account; fix within 10 days | The owner watches the developer account's notifications. A fix ships as a patch release (`docs/RELEASE.md` "Hotfix") | — |

Also followed, from `docs/TDLIB.md` "Compliance notes": the client identifies itself honestly (`deviceModel`,
`applicationVersion` "Pingogram x.y.z"). Pingogram has no automation, mass actions or scraping. `FLOOD_WAIT` is shown
as a countdown and never retried in a loop.

To check before 1.0 (the owner):

- [ ] Telegram's terms page read again; nothing new since 2026-10-22, or the table updated
- [ ] The store listing (`docs/PLAY_STORE.md` §4) and the GitHub README say "Telegram client" prominently, and that it
      is unofficial
- [ ] Sponsored messages seen in a channel that has them, on the release build
- [ ] No notice from Telegram on the developer account

Signed off: _(owner, date)_
