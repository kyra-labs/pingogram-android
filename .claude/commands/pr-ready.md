Prepare the current branch for a pull request:

1. `./gradlew ktlintFormat` then `./gradlew check`; fix all failures.
2. Confirm no secrets, no "Telegram" branding, no hard-coded colors/sizes/tweens, no font family
   other than Google Sans Flex / Monospace, no Material 2 imports (grep for
   `androidx.compose.material.` without the `3`), and the docs/DESIGN_SYSTEM.md §12 audit +
   docs/PERFORMANCE.md §10 checklist for every touched screen.
3. Make sure CHANGELOG.md Unreleased lists the user-visible changes.
4. Write the PR title (Conventional Commit style) and a PR description that fills
   .github/PULL_REQUEST_TEMPLATE.md, including what to test manually.
5. Do NOT push. Show me the summary.
