# Design system

The look of the app, as drawn on the "Android app mockups" canvas. Inspired by the *ergonomics* of
PetKit's own app:
- device cards in a two-column grid;
- a device page built around an illustration with labelled hotspots;
- a bottom action sheet;
- big daily figures compared with yesterday.

The identity itself is original: palette, type and illustrations. Icons are Material Symbols
(below). **No PetKit logo, colour or app artwork is used anywhere.** The one exception, decided by
the owner (issue #69), is PetKit's product photo of each supported model (below, Illustrations),
© PetKit and listed in `NOTICE`; a model without one shows our own simplified drawing.

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
`#E3F1EF`, feeder `#E8EEF7`, fountain `#E2EFF7`, pet `#F6EBDF`, each fading into `background`.

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

Hero tints in dark: litter `#16302D`, feeder `#1B2433`, fountain `#15293A`, pet `#33281C`.
`photoPlate` is what a product photo lies on: nothing in light, `#DDE2E7` in dark (Illustrations). In dark, filled colours
are light and their "on" colours dark, the reverse of the light palette. The live-video screen
is always dark.

Pets get colours in a fixed order so charts and avatars agree everywhere: accent, secondary,
then purple (`#7B4FA8` light, `#C8A2EC` dark) and rose (`#A8436A` light, `#F291B3` dark), each
at least 5.7:1 on `surface`. A Dual-Hopper's hoppers have theirs too, the food drawn in each and
the portions it gave: hopper 1 teal (`#0B7A72` light, `#5CC8BD` dark), hopper 2 orange (`#B45309`
light, `#F2A65A` dark), each at least 4.5:1 on `surface` and always beside its name. Every
coloured state also has a label or a shape, never colour alone: a selected filter chip carries a
check mark, a mode chip says "On" or "Off".

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
| `DeviceCard` | Home | Name, the model's product photo (56 dp, else the family's glyph), headline figure, 24 h strip, status band (idle / busy / offline / error) |
| `AlertBanner` | Home | Warning container, one line, chevron to the device |
| `PetChip` | Home, Pets | Avatar (photo or initial on pet colour), name, weight, visits today |
| `DeviceHero` | Litter, Feeder, Fountain | With the model's photo: the photo fills the hero, each `HotspotPill` right beside a dot on its part (positions per photo, `PhotoSpots`), kept inside the frame, overlapping pills pushed apart, a part the photo does not place under it. Without one: the family's drawing with the pills on either side, positions defined per family in a small layout table |
| `HotspotPill` | Device pages | Label and value, a dot on the illustrated part, colour by state, tappable |
| `ModeChip` | Device header | Shows an on/off mode from a switch entity; on a fountain, only the features that are on, after its battery |
| `StatCompareTile` | Device (litter box, W7H, camera feeder), Pet | Big figure and the change since yesterday: a neutral arrow (Material Symbol, never a good/bad colour) and the amount, read in words by TalkBack ("2 more than yesterday") |
| `DayStemChart` | Litter, Fountain (W7H), camera feeder | 24 h × duration: a stem from the axis per visit, drink or meal eaten, capped with a dot in its pet's colour (unknown pet neutral); events close in time are spread a few dp apart; a drink or meal eaten with no duration a hollow mark on the axis; pet filter; on a camera feeder, inside its Today card, under the tiles |
| `ActionSheet` | Device pages | Pull handle, 4 actions per row, primary action filled; none when the device has no everyday action and every other one is on a part's pill; on a fountain whose mode can be set, the row is its mode (the reported one filled and bold, a tapped one with a spinner until reported), actions behind More |
| `ConfirmDialog` | Destructive actions | Explains the effect, danger-coloured confirm |
| `TimelineRow` | History, device activity | Time rail, icon or pet avatar, title, details, media thumbnails; on a device page, one row per visit, drink or meal (photo, else initial on the pet's colour, else a paw) with the steps that followed indented under a dotted connector, a failed one in `danger` with its reason; on the feeder's day, a meal's portions per hopper in the hopper's colour and the event's clip as a thumbnail at the end, nothing when it has none |
| `DayStrip` | History | 7 days, selected one filled |
| `FilterChips` | History, feeder's day | Single-select; the feeder's day counts each chip ("Meals 4") |
| `SettingRow` | Settings, device settings | Switch, select, number, time, text editors per entity type |
| `LiveCard` | Camera device pages | At the top, under the header and its chips, above the hero: the latest still (16:9, cropped) with "Still from 14:32" in a veiled pill, a round play button in the middle, "Watch live" with a green dot; a plain dark card with the play button before any still. Dark in both themes, like the live screen. One button for TalkBack, naming the device and the still's time |
| `VideoPlayer` | Live | Live badge, measured delay, fullscreen, mute, snapshot |
| `StateViews` | Everywhere | Skeleton loading, empty (illustration and one line), error with retry, offline banner |

Every component has light/dark × EN/FR previews and a screenshot test.

## Illustrations

**Product photos** (issue #69): PetKit's own shot of each supported model, the device alone,
shipped unedited as lossless WebP of at most 900 px in `core:designsystem` (`drawable-nodpi`,
`DevicePhoto`, keyed by the server's codename). They are © PetKit and not covered by the MIT
licence (`NOTICE`). A photo's pixels are never altered: its white background melts into the page
by a multiply blend on what lies under it (the hero's tint, the card's surface), which keeps the
shading of a white product. In dark the same blend would sink the product into the page, so the
photo lies on a light rounded plate (`photoPlate`). The positions of a photo's parts are given in
percent of the photo padded to a square, per photo. Where PetKit sells several
generations under one codename (`t4`, `d4h`, `d4sh`), the photo is the one of the generation the
server reports (`DeviceSummary.generation`), with that photo's pill positions; no generation
(unknown, or a server that predates them, which names the device by its codename alone, "Pura
Max") is the first, so the photo matches the name shown. A generation without a photo (a Pura
Max 3) keeps the drawing, never another generation's photo (`DevicePhoto.of`).

**Drawings**: simple line-and-fill drawings, one per family (litter box, feeder, fountain, camera
litter box), with anchor points for hotspots; what a model without a photo shows.

## Copy

- Short, plain sentences; sentence case; no exclamation marks.
- Device and pet names are user data and are never translated.
- Units come from the locale: "4,3 kg" in French, "4.3 kg" in English.
