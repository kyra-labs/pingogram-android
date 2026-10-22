// Generates core/ui/.../theme/Color.kt: the six color schemes (light/dark × standard/medium/high contrast) of every
// color theme (the brand and the curated ones) from its seed, using Google's material-color-utilities (the Material
// Theme Builder algorithm), plus the base colors that PingogramColors harmonizes at runtime; and the splash screen
// themes of each color theme in :app.
//
// Usage: npm ci && npm run generate
import { readFileSync, writeFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
import { fileURLToPath } from 'node:url';

// material-color-utilities 0.4.0 imports a few of its own modules without the `.js` extension, which
// Node's ESM resolver rejects. Retry those relative specifiers with `.js` appended.
registerHooks({
  resolve(specifier, context, nextResolve) {
    try {
      return nextResolve(specifier, context);
    } catch (error) {
      if (error.code !== 'ERR_MODULE_NOT_FOUND' || !specifier.startsWith('.') || specifier.endsWith('.js')) {
        throw error;
      }
      return nextResolve(`${specifier}.js`, context);
    }
  },
});
const mcu = await import('@material/material-color-utilities');
const { Hct, TonalPalette, argbFromHex } = mcu;

// The color themes of Settings → Appearance → Theme (docs/DESIGN_SYSTEM.md §2.1,
// docs/adr/0016-color-themes.md). Order and names match core:domain's ThemePalette, whose `when` over this table
// stops compiling if one is missing. Pingogram keeps the brand scheme the owner chose (SchemeVibrant, 2026-10-19); the
// curated ones use SchemeTonalSpot, Material Theme Builder's default and Android's default style for wallpaper colors,
// seeded with the Material Design 500 colors; Monochrome uses SchemeMonochrome.
const PALETTES = [
  { name: 'Pingogram', seed: '#2156C4', variant: 'Vibrant' },
  { name: 'Blue', seed: '#2196F3', variant: 'TonalSpot' },
  { name: 'Teal', seed: '#009688', variant: 'TonalSpot' },
  { name: 'Green', seed: '#4CAF50', variant: 'TonalSpot' },
  { name: 'Lime', seed: '#CDDC39', variant: 'TonalSpot' },
  { name: 'Yellow', seed: '#FFEB3B', variant: 'TonalSpot' },
  { name: 'Orange', seed: '#FF9800', variant: 'TonalSpot' },
  { name: 'Red', seed: '#F44336', variant: 'TonalSpot' },
  { name: 'Pink', seed: '#E91E63', variant: 'TonalSpot' },
  { name: 'Purple', seed: '#9C27B0', variant: 'TonalSpot' },
  { name: 'Indigo', seed: '#3F51B5', variant: 'TonalSpot' },
  { name: 'Brown', seed: '#795548', variant: 'TonalSpot' },
  { name: 'Monochrome', seed: '#9E9E9E', variant: 'Monochrome' },
];
// Material green 500; harmonized toward the active scheme's primary at runtime (§2.2, online indicator).
const ONLINE_GREEN = '#4CAF50';

const OUTPUT = fileURLToPath(
  new URL('../../core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/theme/Color.kt', import.meta.url),
);
// Splash screen backgrounds and themes per color theme (docs/DESIGN_SYSTEM.md §8), Pingogram's being hand-written.
const APP = new URL('../../app/src/main/', import.meta.url);
const SPLASH_KOTLIN = fileURLToPath(new URL('kotlin/dev/kyralabs/pingogram/SplashPaletteThemes.kt', APP));
const SPLASH_COLORS = fileURLToPath(new URL('res/values/palette_splash_colors.xml', APP));
const SPLASH_COLORS_NIGHT = fileURLToPath(new URL('res/values-night/palette_splash_colors.xml', APP));
const SPLASH_THEMES = fileURLToPath(new URL('res/values/palette_splash_themes.xml', APP));
const MCU_VERSION = JSON.parse(
  readFileSync(new URL('./node_modules/@material/material-color-utilities/package.json', import.meta.url)),
).version;

// Every androidx.compose.material3.ColorScheme role, in lightColorScheme()/darkColorScheme() parameter order.
const ROLES = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'background', 'onBackground', 'surface', 'onSurface', 'surfaceVariant', 'onSurfaceVariant',
  'surfaceTint', 'inverseSurface', 'inverseOnSurface',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'outline', 'outlineVariant', 'scrim',
  'surfaceBright', 'surfaceContainer', 'surfaceContainerHigh', 'surfaceContainerHighest',
  'surfaceContainerLow', 'surfaceContainerLowest', 'surfaceDim',
  'primaryFixed', 'primaryFixedDim', 'onPrimaryFixed', 'onPrimaryFixedVariant',
  'secondaryFixed', 'secondaryFixedDim', 'onSecondaryFixed', 'onSecondaryFixedVariant',
  'tertiaryFixed', 'tertiaryFixedDim', 'onTertiaryFixed', 'onTertiaryFixedVariant',
];

// Contrast levels in the order of core:domain's Contrast: Standard, Medium, High.
const CONTRASTS = [0.0, 0.5, 1.0];

const hex = (argb) => `0x${(argb >>> 0).toString(16).toUpperCase().padStart(8, '0')}`;
const color = (argb) => `Color(${hex(argb)})`;
const lowerFirst = (name) => name[0].toLowerCase() + name.slice(1);
const snake = (name) => name.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();

function scheme({ seed, variant }, isDark, level) {
  const Scheme = mcu[`Scheme${variant}`];
  if (typeof Scheme !== 'function') throw new Error(`material-color-utilities has no Scheme${variant}`);
  return new Scheme(Hct.fromInt(argbFromHex(seed)), isDark, level);
}

function roles(s) {
  return ROLES.map((role) => {
    const argb = s[role];
    if (typeof argb !== 'number') throw new Error(`material-color-utilities has no role '${role}'`);
    return argb;
  });
}

// One function per theme: its six schemes (light, then dark; each Standard, Medium, High) as 32-bit ARGB, built only
// when asked for, so no theme costs anything before it is chosen.
function paletteFunction(palette) {
  const tables = [false, true].flatMap((isDark) =>
    CONTRASTS.map((level) => {
      const values = roles(scheme(palette, isDark, level)).map((argb) => `                ${hex(argb)}.toInt(),`);
      const index = CONTRASTS.indexOf(level) + (isDark ? 3 : 0);
      // In ktlint's format, so \`./gradlew ktlintFormat\` leaves the output as it is.
      return [
        `        ${index === 5 ? 'else' : index} -> {`,
        '            intArrayOf(',
        ...values,
        '            )',
        '        }',
      ].join('\n');
    }),
  );
  return [
    `/** ${palette.name}: ${palette.seed}, Scheme${palette.variant}. */`,
    `private fun ${lowerFirst(palette.name)}Argb(index: Int): IntArray =`,
    '    when (index) {',
    tables.join('\n\n'),
    '    }',
  ].join('\n');
}

// Custom-color tones from the Material spec: 40 on light schemes, 80 on dark schemes.
const online = TonalPalette.fromInt(argbFromHex(ONLINE_GREEN));

const kotlin = `// Generated by tools/theme/generate.mjs. Do not edit; change the script and run \`npm ci && npm run generate\` there.
// Contrast levels 0.0 / 0.5 / 1.0, @material/material-color-utilities ${MCU_VERSION}.
package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette

/** The scheme of [palette] for [dark] and [contrast], built anew: callers keep it (\`rememberPingogramColorScheme\`). */
internal fun paletteColorScheme(
    palette: ThemePalette,
    dark: Boolean,
    contrast: Contrast,
): ColorScheme {
    val index = contrast.ordinal + if (dark) 3 else 0
    val argb =
        when (palette) {
${PALETTES.map((p) => `            ThemePalette.${p.name} -> ${lowerFirst(p.name)}Argb(index)`).join('\n')}
        }
    return colorSchemeOf(argb)
}

/** The seed each theme's scheme was generated from, for tests and documentation. */
internal fun paletteSeed(palette: ThemePalette): Color =
    when (palette) {
${PALETTES.map((p) => `        ThemePalette.${p.name} -> Color(${hex(argbFromHex(p.seed))})`).join('\n')}
    }

/** A scheme from its ${ROLES.length} roles in the generator's order. */
private fun colorSchemeOf(argb: IntArray): ColorScheme =
    lightColorScheme(
${ROLES.map((role, i) => `        ${role} = Color(argb[${i}]),`).join('\n')}
    )

${PALETTES.map(paletteFunction).join('\n\n')}

/** ${ONLINE_GREEN} at tone 40, before harmonization with the light scheme's primary. */
internal val OnlineLight: Color = ${color(online.tone(40))}

/** ${ONLINE_GREEN} at tone 80, before harmonization with the dark scheme's primary. */
internal val OnlineDark: Color = ${color(online.tone(80))}
`;

writeFileSync(OUTPUT, kotlin);
console.log(`Wrote ${OUTPUT}`);

// Splash screens (docs/DESIGN_SYSTEM.md §8): the background of each theme's light and dark scheme at standard
// contrast. Pingogram's are the hand-written brand_background_* of app/src/main/res/values/colors.xml.
const splashPalettes = PALETTES.filter((p) => p.name !== 'Pingogram');
const background = (p, isDark) => `#${(scheme(p, isDark, 0).background >>> 0).toString(16).toUpperCase().padStart(8, '0')}`;
const header = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/theme/generate.mjs. Do not edit. -->\n';
const colorXml = (isDark) =>
  `${header}<resources>\n${splashPalettes
    .map((p) => {
      const n = snake(p.name);
      const own = background(p, isDark);
      return [
        ...(isDark
          ? []
          : [
              `    <color name="splash_${n}_light">${background(p, false)}</color>`,
              `    <color name="splash_${n}_dark">${background(p, true)}</color>`,
            ]),
        `    <!-- The theme mode "System": ${isDark ? 'dark' : 'light'} here. -->`,
        `    <color name="splash_${n}_system">${own}</color>`,
        `    <color name="splash_${n}_system_amoled">${isDark ? '#FF000000' : own}</color>`,
      ].join('\n');
    })
    .join('\n')}\n</resources>\n`;
writeFileSync(SPLASH_COLORS, colorXml(false));
writeFileSync(SPLASH_COLORS_NIGHT, colorXml(true));
writeFileSync(
  SPLASH_THEMES,
  `${header}<resources>\n${splashPalettes
    .map((p) => {
      const n = snake(p.name);
      return [
        `    <style name="Theme.Pingogram.Starting.System${p.name}">`,
        `        <item name="windowSplashScreenBackground">@color/splash_${n}_system</item>`,
        '    </style>',
        `    <style name="Theme.Pingogram.Starting.System${p.name}Amoled">`,
        `        <item name="windowSplashScreenBackground">@color/splash_${n}_system_amoled</item>`,
        '    </style>',
        `    <style name="Theme.Pingogram.Starting.Light.${p.name}">`,
        `        <item name="windowSplashScreenBackground">@color/splash_${n}_light</item>`,
        '    </style>',
        `    <style name="Theme.Pingogram.Starting.Dark.${p.name}">`,
        `        <item name="windowSplashScreenBackground">@color/splash_${n}_dark</item>`,
        '    </style>',
      ].join('\n');
    })
    .join('\n')}\n</resources>\n`,
);
const styleRef = (p, kind) => `R.style.Theme_Pingogram_Starting_${kind.replace('{}', p.name)}`;
writeFileSync(
  SPLASH_KOTLIN,
  `// Generated by tools/theme/generate.mjs. Do not edit; change the script and run \`npm ci && npm run generate\` there.
package dev.kyralabs.pingogram

import androidx.annotation.StyleRes
import dev.kyralabs.pingogram.core.domain.model.ThemePalette

/**
 * The splash screen theme of [palette] (not Pingogram's, which SplashScreenThemes.kt names): in the system's theme
 * mode ([SplashMode.System], or [SplashMode.SystemAmoled] for true black at night), light or dark.
 */
@StyleRes
internal fun paletteSplashTheme(
    palette: ThemePalette,
    mode: SplashMode,
): Int =
    when (palette) {
        ThemePalette.Pingogram -> {
            error("Pingogram's splash screen themes are SplashScreenThemes.kt's")
        }

${splashPalettes
  .map(
    (p) => `        ThemePalette.${p.name} -> {
            when (mode) {
                SplashMode.System -> ${styleRef(p, 'System{}')}
                SplashMode.SystemAmoled -> ${styleRef(p, 'System{}Amoled')}
                SplashMode.Light -> ${styleRef(p, 'Light_{}')}
                SplashMode.Dark -> ${styleRef(p, 'Dark_{}')}
            }
        }`,
  )
  .join('\n\n')}
    }
`,
);
console.log('Wrote the splash screen themes');
