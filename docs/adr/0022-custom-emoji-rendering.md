# 0022 — Custom emoji: pictures drawn over the fallback emoji, through the sticker pipeline

Date: 2026-10-23 · Status: proposed

## Context

A custom emoji (td_api.tl `textEntityTypeCustomEmoji`) is an entity over a regular emoji in a text; its picture is a
sticker of a custom emoji set (`getCustomEmojiStickers`, at most 200 ids a call): animated (TGS), still (WebP) or a
video (WebM), some single-color ones meant to take the text's color (`stickerFullTypeCustomEmoji.needs_repainting`).
Until now Pingogram showed the regular emoji. Bubbles are the most measured part of the app (`docs/PERFORMANCE.md` §1,
§4: one `Layout` per bubble, cached text layouts, nothing composed again while scrolling).

Ways to put a picture into Compose text:

- **`InlineTextContent`** (a `Placeholder` in the `AnnotatedString`, a composable per placeholder): the text holds no
  emoji in its place (copying and screen readers need the alternate text handled apart), and every custom emoji is a
  composable of its own, measured and placed with the text: more composition per bubble while scrolling, and a
  loaded picture recomposes it.
- **A transparent fallback glyph and a drawn picture**: hiding the glyph through the text's color changes the
  `AnnotatedString`, so the text is laid out again when a picture arrives, and the fallback cannot show meanwhile.
- **Drawing over the laid-out text**: the text keeps the fallback emoji as it is; once a picture is there, the glyph's
  box (from the cached `TextLayoutResult`, as `textBlocks` finds code blocks) is clipped out of the text's drawing
  (`ClipOp.Difference`) and the picture drawn there. Nothing is laid out or composed again.

Animated custom emoji could get a player of their own, or go through `AnimatedStickers`
(`docs/adr/0008-lottie-for-animated-stickers.md`).

## Decision

- **Drawing over the laid-out text**, in a `Modifier.Node` (`core:ui`'s `Modifier.customEmoji`) in a graphics layer
  of its own: the picture fills the largest square of the fallback emoji's box (its width, its line's height), centred.
  Until it loads, and where it never does, the fallback emoji shows; copying, search and screen readers keep the
  emoji. A custom emoji in a hidden spoiler stays hidden; one that takes the text's color is drawn tinted
  (`ColorFilter.tint`, `SrcIn`). In the composer's field the same node draws from the field's layout, its entities and
  its scroll, leaving room for the cursor at both ends.
- **One pipeline**: TGS custom emoji play through the app's `AnimatedStickers` (its decoded animations, its first-frame
  cache by file and size, its render threads, its pause rules); each one that plays takes one of the same 8 slots as a
  sticker (the same id twice in a text takes one). WebP ones load through Coil's memory cache at their size; WebM
  ones show their thumbnail, as stickers do. Reactions draw theirs with the same machinery (`CustomEmojiImage`).
- **Loading**: `CustomEmojiRepository` keeps what TDLib returned by id for the session (deduplicated, batched by 200,
  cancellable, emptied when TDLib closes); the conversation asks for the ids of the messages on screen and of the
  composer only, at most every 100 ms, and hands the result to the screen outside `ChatUiState`, read only by the
  nodes' coroutines and draw phase.
- **No picker**: Pingogram adds no custom emoji to what the user writes (sending them needs Telegram Premium in most
  chats). Those a draft or an edited message already holds keep their entity; an edit inside one removes it.

## Consequences

- Loading or animating a custom emoji redraws one text's layer, never a bubble's composition (`ChatRecompositionTest`).
- Sharing the 8 slots means a screen with many animated custom emoji and stickers plays the first 8 and shows the rest
  still, as it already did for stickers beyond 8.
- A clipped glyph box also hides a selection highlight behind the picture (the lifted message's text selection).
- The chat list's previews, reply quotes and notifications keep the fallback emoji: the chat list row is the most
  measured row of the app, and a static picture there was left for later.
