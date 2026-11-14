# Design system

The look of the app, as drawn on the "Android app mockups" canvas. Inspired by the *ergonomics* of
PetKit's own app:
- device cards in a two-column grid;
- a device page built around an illustration with labelled hotspots;
- a bottom action sheet;
- big daily figures compared with yesterday.

The identity itself is original: palette, type, icons and illustrations. **No PetKit image, logo,
colour or product photo is used anywhere**, and device illustrations are our own simplified
drawings.

## Tokens

Defined once in `core:designsystem` (`PkColors`, `PkTypography`, `PkShapes`, `PkSpacing`) and
exposed through a `PkTheme` composition local; screens never use raw values.

### Colour (light)

| Token | Value | Use |
|---|---|---|
| `background` | `#EEF0F3` | Screen background |
| `surface` | `#FFFFFF` | Cards, sheets |
| `surfaceMuted` | `#F1F3F6` | Secondary buttons, tiles |
| `onSurface` | `#1B1F24` | Primary text |
| `onSurfaceMuted` | `#5B6470` | Secondary text (4.6:1 on white) |
| `accent` | `#0B7A72` | Primary actions, selected tab, OK state (5.2:1 on white) |
| `accentContainer` | `#E6F4F2` | Accent backgrounds, "idle" band |
| `onAccentContainer` | `#075E58` | Text on accent backgrounds |
| `secondary` | `#2E4C78` | Second pet colour, informative chips |
| `warning` | `#B45309` | Attention states, alert dots |
| `warningContainer` | `#FFF4E5` | Alert banners and pills |
| `onWarningContainer` | `#7A3A06` | Text on alert backgrounds |
| `success` | `#1F7A3A` | "Served", "idle" status text |
| `danger` | `#B42318` | Sign out, destructive confirmation |
| `outline` | `#D5D9DF` | Field borders, separators |

Device pages use a soft vertical gradient behind the hero, tinted per family: litter
`#E3F1EF`, feeder `#E8EEF7`, pet `#F6EBDF`, each fading into `background`.

A dark palette mirrors every token (to be drawn and checked for contrast at project start); the
live-video screen is always dark.

Pets get colours in a fixed order (accent, secondary, then two more to define) so charts and
avatars agree everywhere. Every coloured state also has a label or a shape, never colour alone.

### Type

| Style | Font | Size / weight |
|---|---|---|
| Display (home title) | Plus Jakarta Sans | 28 / 800 |
| Headline (section, big figures) | Plus Jakarta Sans | 22–34 / 800 |
| Title (card, page) | Inter | 15–20 / 600–700 |
| Body | Inter | 14–15 / 400–500 |
| Label | Inter | 11–13 / 500–600 |

Both fonts are SIL OFL and bundled (no network font loading). Sizes are in `sp` and follow the
user's font scale; layouts are checked at 130 %.

### Shape and spacing

- Radius:
  - cards 22–24 dp;
  - tiles and pills 16–18 dp;
  - buttons and fields 14–16 dp;
  - chips fully rounded;
  - bottom sheet top corners 26 dp.
- Spacing scale 4 dp; screen gutter 16–20 dp; gap between cards 12 dp.
- Elevation is a hairline shadow only (cards), plus a soft upward shadow under the action sheet.

## Components

| Component | Where | Notes |
|---|---|---|
| `DeviceCard` | Home | Glyph, name, headline figure, 24 h strip, status band (idle / busy / offline / error) |
| `AlertBanner` | Home | Warning container, one line, chevron to the device |
| `PetChip` | Home, Pets | Avatar (photo or initial on pet colour), name, weight, visits today |
| `DeviceHero` | Litter, Feeder | Illustration plus positioned `HotspotPill`s; positions defined per family in a small layout table |
| `HotspotPill` | Device pages | Label and value, a dot on the illustrated part, colour by state, tappable |
| `ModeChip` | Device header | Shows an on/off mode from a switch entity |
| `StatCompareTile` | Device, Pet | Big figure and the difference from yesterday |
| `DayScatterChart` | Litter | 24 h × duration dots, coloured per pet, pet filter |
| `ActionSheet` | Device pages | Pull handle, 4 actions per row, primary action filled |
| `ConfirmDialog` | Destructive actions | Explains the effect, danger-coloured confirm |
| `TimelineRow` | History, device activity | Time rail, icon or pet avatar, title, details, media thumbnails |
| `DayStrip` | History | 7 days, selected one filled |
| `FilterChips` | History | Single-select |
| `SettingRow` | Settings, device settings | Switch, select, number, time, text editors per entity type |
| `VideoPlayer` | Live | Live badge, measured delay, fullscreen, mute, snapshot |
| `StateViews` | Everywhere | Skeleton loading, empty (illustration and one line), error with retry, offline banner |

Every component has light/dark × EN/FR previews and a screenshot test.

## Illustrations

Simple line-and-fill drawings, one per family (litter box, feeder, fountain, camera litter box),
drawn as vector drawables with named anchor points for hotspots. Model-specific drawings can come
later; a family drawing is always the fallback.

## Copy

- Short, plain sentences; sentence case; no exclamation marks.
- Device and pet names are user data and are never translated.
- Units come from the locale: "4,3 kg" in French, "4.3 kg" in English.
