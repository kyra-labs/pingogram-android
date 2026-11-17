---
paths:
  - "**/src/**/res/**"
  - "**/AndroidManifest.xml"
---

# Android resources

- Strings: English only, in the module's `res/values/strings.xml`. Names are
  `<module>_<what>` in snake_case (`chat_open_profile`).
- Use `<plurals>` for counts and numbered placeholders (`%1$s`); never build a sentence by
  concatenation. Put a translator comment above any string with a placeholder.
- Never create or edit `res/values-<language>/`: translations come from Weblate
  (`docs/TRANSLATING.md`).
- Manifest: adding a permission, a component with `exported="true"`, or a foreground service
  type needs the owner's approval first.
- Icons are Material Symbols or our own artwork in `art/`. Nothing from Telegram's apps.
