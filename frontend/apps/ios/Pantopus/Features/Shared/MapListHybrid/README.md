# MapListHybrid · iOS

The shared shell every full-bleed-map + draggable-bottom-sheet
screen on iOS ships on top of. Introduced in T6.6a (P24) to express
the **Nearby map** redesign — and built to be reused by Marketplace
(gig location) or Discover Businesses (business location) when
those want the same pattern.

| File | Role |
|---|---|
| `MapListHybridShell.swift` | The view shell — wraps a full-bleed `WorldMap` view + a 3-detent SwiftUI `.sheet` (160 / 296 / 518 pt) + 6 floating slots (top pill · category chips · map controls · sheet header · sheet body · per-pin overlay). |
| `MapListHybridContent.swift` | `MapListHybridContent`, `MapPin`, `MapListHybridDetent` (`.collapsed` / `.standard` / `.expanded`), `MapListHybridDetentResolver` (the snap-to-nearest + velocity-nudge math shared with Android + web). |
| `MapListHybridPreview.swift` | Static designer canvas with sample pins, reachable on iOS via the debug Token gallery → "Map+List hybrid shell" (`MapListHybridPreviewHost`). |

## When to use this shell

Reuse the shell whenever the design is:

> a full-bleed map with a draggable bottom sheet that snaps between
> a header-only collapsed state, a header + carousel standard state,
> and a header + full list expanded state.

T6.6a is the only consumer today (`Nearby` tab — `NearbyMapView`).
Future candidates: Marketplace map mode, Discover Businesses map
view, gig-location preview from a Gig detail.

**If a future map-list consumer needs a 4th detent** or a different
sheet anchor scheme, propose an additive extension to
`MapListHybridDetent` here first — never change the existing
absolute heights (160 / 296 / 518) without coordinated iOS +
Android + web changes; the detents are part of the visual contract.

## Anatomy

```
┌──────────────────────────────────────────────────────┐
│ ◀  Title  · filters                                  │  TopPill   (floating, blur bg)
│                                                      │
│ [All] [Handyman] [Cleaning] [Pet care] [Plumbing]    │  CategoryChips (overlay)
│                                                      │
│                                                      │
│                                                      │
│       ⊙ pin   ⊙ pin                                  │  MapLayer (full-bleed,
│                                                      │             shell-owned)
│                       ⊙ pin                          │
│                                                      │
│                                            🎯        │  MapControls (right edge,
│                                            ▦         │   floating above sheet)
│                                                      │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│  ╭───── Sheet header ─────────────────────────────╮ │  SheetHeader (sticky)
│  │ 12 · Sort: Nearest                              │ │
│  ╰─────────────────────────────────────────────────╯ │
│  ╭───── Sheet body ───────────────────────────────╮ │  SheetBody (carousel
│  │ [card] [card] [card] [card] (standard)         │ │   or list)
│  ╰─────────────────────────────────────────────────╯ │
└──────────────────────────────────────────────────────┘
```

## Slot contract

Each slot is a renderless prop; consumers supply whatever chrome
they need.

| # | Slot | Role |
|---|---|---|
| 1 | Map layer | Shell-owned. iOS uses MapKit. Renders supplied `[MapPin]` with the design's coloured-disc + white-ring (confirmed) or dashed-outline (pending) treatment, plus an optional "you are here" anchor disc. |
| 2 | Top pill | Floating back / title / filter pill on the top edge (blur background). |
| 3 | Category chips | Overlay horizontal strip sitting under the top pill. |
| 4 | Map controls | Locate-fixed / layers buttons stacked on the right edge, automatically anchored above the current sheet height. |
| 5 | Sheet header | Count label + sort selector inside the sheet. |
| 6 | Sheet body | The variable part — carousel for `.standard`, list for `.expanded`, drag-up prompt for `.collapsed`. |

## Detent contract

Three detents, absolute heights (per Q9 of the buildout-plan
decisions doc — same numbers on iOS, Android, web so the same
gesture lands at the same stop):

| Detent | Height | Behaviour |
|---|---|---|
| Collapsed | 160 pt | header + drag-to-expand prompt |
| Standard  | 296 pt | header + horizontal card carousel |
| Expanded  | 518 pt | header + full vertical list |

iOS implementation:
`.presentationDetents([.height(160), .height(296), .height(518)])` on
a `.sheet`. The `MapListHybridDetentResolver` is the pure function
that maps a release height + velocity to the snapped detent. Sign
convention:

- positive velocity = downward flick → shrink one detent
- negative velocity = upward flick → grow one detent
- magnitudes equal to the threshold do **not** nudge — strict `>` /
  `<` only — so the threshold edge defers to snap-to-nearest
- snap-to-nearest by absolute distance to the released height when
  neither flick branch fires

Velocity *units* differ by platform because the underlying gesture
systems do; **the resolver threshold is calibrated per-platform so
the same physical flick crosses it on every device**:

| Platform | Units | Threshold |
|---|---|---|
| iOS | UIKit points / second | 600 (`velocityThreshold`) |
| Android | raw pixels / second (Compose `draggable`) | 1 200 (`VELOCITY_THRESHOLD`) |
| Web | CSS pixels / second (pointer events) | 600 (`MAP_LIST_HYBRID_VELOCITY_THRESHOLD`) |

## Pin↔list selection sync

Pin↔list selection sync is the consumer's job: the shell fires
`onPinTap(id)` when a pin is tapped; the consumer typically (a)
updates its own selection state and (b) snaps `detent` to
`.standard` so the matching card surfaces. The active pin draws a
1.6s dual-halo pulse on iOS (suppressed under reduce-motion;
static ring instead).

## Test tag parity (root container)

All three platforms expose the same set of test identifiers on the
shell-owned wrappers, so cross-platform UI tests can mirror by
string:

| Test ID | Wraps |
|---|---|
| `mapListHybridShell` | full canvas root |
| `mapListHybridMap` | the live map layer |
| `mapListHybridTopPill` | top-pill slot wrapper |
| `mapListHybridChips` | category-chips slot wrapper |
| `mapListHybridMapControls` | map-controls slot wrapper |
| `mapListHybridSheet` | the rounded bottom-sheet container |
| `mapListHybridSheetHeader` | sheet-header slot wrapper |
| `mapListHybridSheetBody` | sheet-body slot wrapper |
| `mapListHybridPin_<id>` | per-pin marker |

iOS uses `accessibilityIdentifier`; Android uses `Modifier.testTag`;
web uses `data-testid`. Slot *contents* carry their own consumer-
supplied identifiers — the shell only tags its own wrappers.

## Lifecycle states

Every fetchable map-list surface ships **four**:

1. **`.loading`** — map renders empty; sheet shows a shimmer skeleton
   that mirrors the loaded carousel / list geometry.
2. **`.empty`** — "Nothing nearby" headline + radius-widening CTA
   inside the sheet body.
3. **`.loaded`** — pins on the map + carousel / list in the sheet.
4. **`.error(message:)`** — `ErrorBanner` with `Retry` wired to
   `viewModel.refresh()` inside the sheet header.

## See it rendered

Reachable on iOS via the debug Token gallery → "Map+List hybrid
shell" (`MapListHybridPreviewHost`); also on web at
`/map-list-hybrid-preview` (every detent rendered side-by-side with
live Mapbox tiles + sample pins).

## Snapshot baselines

Design-reference PNGs:
`frontend/apps/ios/PantopusTests/__Snapshots__/t6/map-list-hybrid.png`
(default `.standard` detent) + `map-list-hybrid-collapsed.png` +
`map-list-hybrid-expanded.png`.

Drift is caught by `T6ScreensSnapshotTests.swift` (file-presence +
non-trivial PNG assertions). Future SwiftUI snapshot tests can pin
the on-device render against the design references.

## Adding a new map-list consumer

1. **Compose** a view-model that exposes the map-list state
   (`[MapPin]`, list rows, selected pin id) + four lifecycle
   states.
2. **Wrap** the view in `MapListHybridShell { … }` and supply the
   6 slots. The shell handles the map layer + detent gestures.
3. **Wire** `onPinTap(id)` to update the selected state and snap
   `detent` to `.standard`.
4. **Tests.** `*ViewModelTests.swift` covers
   load → loaded / empty / error + selection sync. Snapshot
   baseline lands in `T6ScreensSnapshotTests.swift` for each
   detent.
5. **Parity audit + wiring audit** rows for the new consumer.

The longer doc-of-record is in `docs/t6-buildout-plan.md` §E.2 and
`docs/mobile-parity-audit.md` "T6.6a — MapListHybrid archetype
shell (P24)" preamble.

— Pantopus T6.6a
