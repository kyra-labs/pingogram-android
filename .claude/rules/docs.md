---
paths:
  - "**/*.md"
---

# Docs and changelog

- `CHANGELOG.md` → Unreleased: one entry per user-visible change, one or two lines, written
  for a user. No implementation detail. Nothing for refactors, tests, CI or docs.
- Update a doc in the same PR as the code it describes. Edit the existing section; never
  append a paragraph that restates an older one.
- A doc states a rule or a decision, not the story of how it was reached.
- A new significant decision gets `docs/adr/NNNN-title.md` and a row in `docs/adr/README.md`.
