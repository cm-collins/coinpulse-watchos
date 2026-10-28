# CoinPulse Design System ("Obsidian")

Status: **proposal, not yet implemented.** Nothing here is in code. Palette, spacing and motion values are *design decisions proposed by us*, informed by the official Wear OS Material 3 Expressive structure; they are not values copied from any other app. Contrast ratios were computed locally (WCAG relative luminance).

## 1. Research basis and its limits
Read (official):
- Wear OS M3 Expressive overview: https://developer.android.com/design/ui/wear/guides/get-started/apply
- Color roles/tokens: https://developer.android.com/design/ui/wear/guides/styles/color/roles-tokens
- Typography (fonts, type scale, accessibility): https://developer.android.com/design/ui/wear/guides/styles/typography/fonts , `.../type-scale-tokens` , `.../accessibility`
- Launch post: https://android-developers.googleblog.com/2025/08/introducing-material-3-expressive-for-wear-os.html

Not available:
- **Mobbin could not be accessed** (HTTP 403 / login wall). No Mobbin screens were reviewed. Crypto-app inspiration below comes from general web results (vendor/portfolio pages, low authority) and should be treated as patterns, not evidence. **Action for the team:** browse Mobbin manually (Finance → Coinbase, Robinhood, Revolut, Kraken, Binance; also Apple Watch stock/crypto screens) and add notes to this file.
- No exact Wear type-scale sizes (sp/weight/line height) were retrievable as text; use the `MaterialTheme.typography` roles from the Wear Compose library instead of inventing sizes.

Key official takeaways used:
- M3 Expressive: deeper tonal palettes, tertiary color for accents, variable fonts (Roboto Flex, weight + width axes), edge-hugging buttons, shape-morphing, `TransformingLazyColumn`.
- Color roles come in base/dim/container sets with on-colors; role pairs aim for ≥3:1 contrast; error red must stay distinguishable from warning/success.
- Display and Numeral styles don't scale with user font size; styles ≥20sp don't scale. Title/Label(M,S)/Body scale in ±6% steps → design for smallest and largest.
- Numerals: use tabular/mono figure spacing when values change or animate. Avoid very light weights for small text. Narrow width axis for long numbers.
- Round screens have less usable area and need larger text margins; keep glanceable.

## 2. Concept
**Obsidian & Signal.** True-black OLED canvas, softly layered ink surfaces, one cool accent, and *signal colors reserved for market direction*. Premium comes from restraint: few colors, big confident numerals, generous spacing, precise motion, zero decoration without meaning. One hero number per screen.

## 3. Color (proposal)
Dark only on the watch (OLED, battery). Implement as a Wear `ColorScheme` plus a small extension (`CoinPulseColors`) for gain/loss/stale via a `CompositionLocal`, since M3 has no gain/loss roles.

| Token | Hex | Use |
|---|---|---|
| background | `#000000` | Canvas (OLED black) |
| surfaceContainerLow | `#0B0D12` | Non-interactive cards |
| surfaceContainer | `#12151C` | Default cards/rows |
| surfaceContainerHigh | `#1A1E28` | Emphasized/pressed cards |
| onSurface | `#ECEFF6` | Primary text |
| onSurfaceVariant | `#9AA3B5` | Secondary text, timestamps |
| outlineVariant | `#2B3140` | Decorative hairlines only |
| primary | `#8AA4FF` | Primary actions, selection, sparkline neutral |
| onPrimary | `#0A1033` | Text on primary |
| primaryContainer | `#1F2A5C` | Selected/active fills |
| onPrimaryContainer | `#DCE3FF` | Text on primaryContainer |
| tertiary (alert/stale) | `#FFC857` | Stale data, price alerts |
| error | `#FF6B7A` | Errors (shares hue family with loss; use icon + text) |
| **gain** | `#3DDC97` | Positive change |
| **loss** | `#FF6B7A` | Negative change |

Computed contrast (WCAG): onSurface/background 18.24:1; onSurface/surfaceContainer 15.87:1; onSurfaceVariant/background 8.28:1, /surfaceContainer 7.20:1; gain/background 11.88:1, /surfaceContainer 10.33:1; loss/background 7.64:1, /surfaceContainer 6.64:1; amber/surfaceContainer 11.87:1; primary/background 8.83:1; onPrimary/primary 7.79:1; onPrimaryContainer/primaryContainer 10.67:1.
**Caveat:** gain vs loss have only 1.56:1 luminance contrast against each other, i.e. similar brightness. **Direction must never rely on hue**: always pair with `+`/`−` sign and ▲/▼ glyph (and content description). Offer a colorblind-safe alternative palette (e.g. blue/orange) as a later setting; do not ship it without checking with real simulation.
- Optionally support dynamic color later (the library has `dynamicColorScheme`), but keep gain/loss fixed for meaning. Decide explicitly; default is fixed brand palette.
- Hex values above are first-pass; adjust after viewing on device and re-run contrast checks. Never hard-code hex in screens — only in the theme.

## 4. Typography
- **Family: Roboto Flex** (official recommendation for Wear M3 Expressive; variable weight/width). Roboto Flex is distributed through Google Fonts; **verify its license (believed SIL OFL, not confirmed from the page we could load) and the Wear Compose font-integration API before bundling**. Fallback: the system font via `MaterialTheme` defaults, so the app works without bundling anything. Bundling a variable font adds APK size; measure.
- Use Wear `MaterialTheme.typography` roles; don't hard-code sizes. Roles: **Numeral** for the hero price (doesn't scale; keep it short), **Display** for rare hero moments, **Title** for screen/section titles, **Label** for buttons/chips, **Body** for metadata/timestamps, **Arc** for curved titles.
- Prices: tabular figures, medium–semibold weight, narrower width axis for long values. Symbols (`BTC`): label role, tracking slightly open, uppercase. No light weights below title size.
- Percent change: label/body role with sign and arrow. Timestamps ("Updated 2m ago"): body, `onSurfaceVariant`.
- Test at the smallest and largest system font sizes.

## 5. Icons
- **Material Symbols** (Rounded, weight ~400, optical size small) for UI glyphs: refresh, star/watchlist, notifications, settings, phone, error, wifi_off, arrow_drop_up/down. Material Symbols are Apache-2.0 per Google (confirm on fonts.google.com/icons before shipping).
- Prefer the minimal set of vector drawables imported individually; avoid `material-icons-extended` (very large) — check what the existing Compose Material 3 Wear artifacts already provide first.
- **Coin logos:** do not hot-link or bundle third-party logos without checking their licensing/terms. MVP: a neutral monogram avatar (first letter or symbol on a tinted `surfaceContainerHigh` circle). Logos can be a later, license-checked enhancement.
- App/tile icon: simple monochrome pulse mark on a dark background; provide adaptive + monochrome (themed) variants.

## 6. Shape, spacing, layout
- Shapes: use M3 Wear container shapes (large corner radii on rows, edge-hugging button at the bottom). Let rows morph on press via the component defaults rather than custom animation.
- Spacing scale (dp): 4, 8, 12, 16, 24. Use scaffold `contentPadding`; keep key content off the extreme corners of round screens.
- Screens (watch MVP):
  1. **Watchlist** (`TransformingLazyColumn`): row = monogram, symbol, price (Numeral/Label), ▲/▼ percent. Header shows market status/last updated. Edge button: "Add"/"More".
  2. **Coin detail**: hero price, percent + abs change, sparkline (24h) in `primary`/signal color, high/low/volume as quiet secondary rows. Stale banner in tertiary.
  3. **States**: skeleton placeholders (use the library's placeholder/shimmer components), empty watchlist with single CTA, offline/stale/rate-limited messages with retry.
  4. **Settings/alerts** minimal; heavier configuration lives on the phone.
- Tile / widget (later): one coin, price + change, three-slot layout; see `android-skills:wear-widgets`.
- Phone companion (later): same tokens in Material 3 (phone) with a larger chart, portfolio-style watchlist management; keep read-only and never request keys or seed phrases.

## 7. Motion
- Use library defaults: `TransformingLazyColumn` scroll transformations, shape morphing on buttons, scroll indicator.
- Price updates: brief, subtle (≤200ms) flash/crossfade on the numeral; no tickers rolling constantly. Respect reduced-motion/battery: no infinite animations; static in ambient mode (dim, no color-heavy fills, burn-in-safe).
- Loading: placeholders, not spinners alone.

## 8. Accessibility (must-haves)
- Direction conveyed by sign + arrow + color. Reading order for TalkBack: symbol → price → change → freshness ("Bitcoin, 67,123 dollars, up 2.31 percent, updated 2 minutes ago").
- Support font scaling; no clipped text at largest size; touch targets from stock components (custom ≥48dp).
- Contrast: keep ≥ the computed ratios above; re-check any new color.

## 9. "Premium" checklist
- [ ] OLED black background, max two surface levels per screen.
- [ ] One hero number; everything else clearly subordinate.
- [ ] Gain/loss color only on change values and sparkline, nowhere decorative.
- [ ] Tabular numerals; no layout jumps on update.
- [ ] Skeletons, calm stale/error states, no raw error strings.
- [ ] Consistent spacing scale and icon weight.
- [ ] Screenshots reviewed on small and large round emulators and at both font extremes.

## 10. Implementation notes (when we build it)
- Theme code lives in `presentation/theme/` (`Color.kt`, `Type.kt`, `Theme.kt`, `CoinPulseColors`). Extend the existing `CoinpulseTheme`; no new design-system module.
- Fonts/resources only through Gradle resources and the version catalog; no external CDNs at runtime.
- Add `@WearPreviewDevices` / `@WearPreviewFontScales` previews for every state (see `wear-ui` skill).
