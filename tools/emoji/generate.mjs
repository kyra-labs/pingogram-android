// Writes the emoji panel's catalog (core/data/src/main/res/raw/emoji_catalog.txt) from unicode-emoji-json, Unicode's
// emoji-test.txt as JSON: the fully-qualified emoji Recommended for General Interchange, in Unicode's groups and order,
// skin tone variants folded into their base emoji. One line per group: its slug, a tab, its emoji separated by spaces.
// The "Component" group (bare skin tones and hair styles) is not offered. Run `npm ci && npm run generate`; never
// hand-edit the output. The app leaves out, at run time, emoji the device's fonts cannot draw.
import { readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const groups = JSON.parse(readFileSync(require.resolve("unicode-emoji-json/data-by-group.json"), "utf8"));
const { version } = JSON.parse(readFileSync(require.resolve("unicode-emoji-json/package.json"), "utf8"));

const lines = groups
  .filter((group) => group.slug !== "component")
  .map((group) => {
    const emoji = group.emojis.map((entry) => entry.emoji);
    if (emoji.some((it) => /\s/u.test(it))) throw new Error(`white space in an emoji of ${group.slug}`);
    return `${group.slug}\t${emoji.join(" ")}`;
  });

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "../../core/data/src/main/res/raw/emoji_catalog.txt");
writeFileSync(out, `${lines.join("\n")}\n`);
console.log(`unicode-emoji-json ${version}: ${lines.length} groups, ${lines.map((l) => l.split(" ").length).reduce((a, b) => a + b)} emoji -> ${out}`);
