# Party Queue — branding

**Name:** Party Queue · **Tagline:** *kolejka na każdą imprezę*
**Mark:** three equalizer bars with a lime spark on the brand gradient. The bars dance while music plays.

## Colours

| Role | Hex |
|------|-----|
| Pink (gradient start) | `#FF2E93` |
| Violet (gradient middle) | `#7C3AED` |
| Blue (gradient end) | `#22B8FF` |
| Orange (accent) | `#FF9F1C` |
| Lime (spark) | `#B8F34A` |
| Dark background | `#130A26` |
| Light background | `#FFF7FC` |

The gradient always runs pink → violet → blue, diagonally (or left to right in bars).

## Files

- `icon.svg` — the app icon (512 px, rounded square). The Android launcher icon is the same drawing as vector
  drawables in `app/src/main/res/drawable/ic_launcher_*.xml` (adaptive; with a monochrome layer for themed icons).
- `banner.svg` — README banner.
- The guest page carries the mark as an inline favicon (`app/src/main/assets/guest/index.html`).
- `app/src/main/java/pl/mleczki/partyqueue/PartyTheme.kt` holds the colours and both colour schemes;
  `PartyComponents.kt` the drawn logo, equalizer, buttons and glyphs.

## Style

Gradients and soft glows; rounded shapes (16–30 dp); bold type; motion that has a purpose — the now-playing card
slowly drifts, the equalizer follows playback, tabs slide. Respect "reduce motion" on the web page.
