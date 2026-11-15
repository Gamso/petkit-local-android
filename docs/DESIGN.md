# Design system

The look of the app, as drawn on the "Android app mockups" canvas. Inspired by the *ergonomics* of
PetKit's own app:
- device cards in a two-column grid;
- a device page built around an illustration with labelled hotspots;
- a bottom action sheet;
- big daily figures compared with yesterday.

The identity itself is original: palette, type and illustrations. Icons are Material Symbols
(below). **No PetKit image, logo,
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

Three "on" tokens complete the table for text and icons on filled colours: `onAccent`
`#FFFFFF` (5.2:1), `onSecondary` `#FFFFFF` (8.7:1), `onDanger` `#FFFFFF` (6.6:1).

### Colour (dark)

Every token has a dark value. Text tokens keep at least 4.5:1 on the surfaces they are drawn on:
`PkColorsContrastTest` (core:designsystem) computes the WCAG ratio of every such pair in both
palettes and fails the build below 4.5:1. Ratios are against `surface` unless stated.

| Token | Value | Contrast |
|---|---|---|
| `background` | `#101316` | — |
| `surface` | `#1A1E23` | — |
| `surfaceMuted` | `#252B32` | — |
| `onSurface` | `#E6E9ED` | 13.8:1 (15.3:1 on `background`, 11.7:1 on `surfaceMuted`) |
| `onSurfaceMuted` | `#A4ADB9` | 7.4:1 (6.3:1 on `surfaceMuted`) |
| `accent` | `#5CC8BD` | 8.3:1 |
| `onAccent` | `#00211E` | 8.5:1 on `accent` |
| `accentContainer` | `#123A36` | — |
| `onAccentContainer` | `#A6E6DE` | 8.9:1 on `accentContainer` |
| `secondary` | `#9DB8E3` | 8.3:1 |
| `onSecondary` | `#0D1B30` | 8.6:1 on `secondary` |
| `warning` | `#F2A65A` | 8.3:1 |
| `warningContainer` | `#3A2810` | — |
| `onWarningContainer` | `#FFD8A8` | 10.5:1 on `warningContainer` |
| `success` | `#74CF8F` | 8.8:1 |
| `danger` | `#FF8B7E` | 7.4:1 |
| `onDanger` | `#3A0904` | 7.6:1 on `danger` |
| `outline` | `#3A424C` | Separators only, like its light value |

Hero tints in dark: litter `#16302D`, feeder `#1B2433`, pet `#33281C`. In dark, filled colours
are light and their "on" colours dark, the reverse of the light palette. The live-video screen
is always dark.

Pets get colours in a fixed order so charts and avatars agree everywhere: accent, secondary,
then purple (`#7B4FA8` light, `#C8A2EC` dark) and rose (`#A8436A` light, `#F291B3` dark), each
at least 5.7:1 on `surface`. Every coloured state also has a label or a shape, never colour
alone: a selected filter chip carries a check mark, a mode chip says "On" or "Off".

### Type

| Style | Font | Size / weight |
|---|---|---|
| Display (home title) | Plus Jakarta Sans | 28 / 800 |
| Headline (section, big figures) | Plus Jakarta Sans | 22–34 / 800 |
| Title (card, page) | Inter | 15–20 / 600–700 |
| Body | Inter | 14–15 / 400–500 |
| Label | Inter | 11–13 / 500–600 |

Both fonts are SIL OFL and bundled (no network font loading): the variable files from Google
Fonts (`google/fonts`, `ofl/inter` and `ofl/plusjakartasans`), each weight an instance of the
`wght` axis, with their licences in `core/designsystem/licenses/`. Sizes are in `sp` and follow
the user's font scale; layouts are checked at 130 %.

### Icons

Material Symbols first: Google's Material 3 icon set, Rounded style, weight 400, unfilled, 24 dp,
under the Apache License 2.0 (`core/designsystem/licenses/`). They live in `PkIcons` as path data
copied from `google/material-design-icons` (the source commit and each symbol's name are in the
file), not through the Compose material-icons libraries, which stopped at 1.7.8. An icon is drawn
for the project only where no symbol fits. Icon-only controls are `PkIconButton` (48 dp, round, on
the surface colour) with a TalkBack label; the way back from a screen is `PkBackButton`, a left
arrow mirrored in right-to-left languages.

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
