# MapListHybrid · web

The shared shell every full-bleed-map + draggable-bottom-sheet
screen on web ships on top of. Introduced in T6.6a (P24) to express
the **Nearby map** redesign — and built to be reused by Marketplace
(gig location) or Discover Businesses (business location) when
those want the same pattern.

| File | Role |
|---|---|
| `MapListHybridShell.tsx` | The shell — wraps a full-bleed leaflet map + a 3-detent draggable bottom-sheet (160 / 296 / 518 px) + 6 floating slots. |
| `MapListHybridMapLayer.tsx` | Dynamic-import leaflet wrapper (skeletons during the lazy load). |
| `types.ts` | `MapListHybridContent`, `MapPin`, `MapListHybridDetent` (`'collapsed' \| 'standard' \| 'expanded'`), `resolveMapListHybridDetent` (the snap-to-nearest + velocity-nudge math shared with iOS + Android). |

## When to use this shell

Reuse the shell whenever the design is:

> a full-bleed map with a draggable bottom sheet that snaps between
> a header-only collapsed state, a header + carousel standard state,
> and a header + full list expanded state.

T6.6a is the only consumer today (web `/app/map`). Future
candidates: Marketplace map mode, Discover Businesses map view, gig-
location preview from a Gig detail.

## Anatomy

```
┌──────────────────────────────────────────────────────┐
│ ◀  Title  · filters                                  │  TopPill   (floating, blur bg)
│                                                      │
│ [All] [Handyman] [Cleaning] [Pet care] [Plumbing]    │  CategoryChips (overlay)
│                                                      │
│       ⊙ pin   ⊙ pin                                  │  MapLayer  (full-bleed,
│                                                      │             shell-owned)
│                       ⊙ pin                          │
│                                                      │
│                                            🎯        │  MapControls (right edge,
│                                            ▦         │   floating above sheet)
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
| 1 | Map layer | Shell-owned. Web uses Leaflet via `react-leaflet`. Renders supplied `MapPin[]` with the design's coloured-disc + white-ring (confirmed) or dashed-outline (pending) treatment, plus an optional "you are here" anchor disc. |
| 2 | Top pill | Floating back / title / filter pill on the top edge (blur background). |
| 3 | Category chips | Overlay horizontal strip sitting under the top pill. |
| 4 | Map controls | Locate-fixed / layers buttons stacked on the right edge, automatically anchored above the current sheet height. |
| 5 | Sheet header | Count label + sort selector inside the sheet. |
| 6 | Sheet body | The variable part — carousel for `'standard'`, list for `'expanded'`, drag-up prompt for `'collapsed'`. |

## Detent contract

Three detents, absolute heights (per Q9 of the buildout-plan
decisions doc — same numbers on iOS, Android, web):

| Detent | Height | Behaviour |
|---|---|---|
| `'collapsed'` | 160 px | header + drag-to-expand prompt |
| `'standard'`  | 296 px | header + horizontal card carousel |
| `'expanded'`  | 518 px | header + full vertical list |

Web implementation: pointer-events on the sheet header drive an
`anchoredDraggableState`-equivalent state machine. The
`resolveMapListHybridDetent` is the pure function that maps a
release height + velocity to the snapped detent. Sign convention
(matches iOS + Android):

- positive velocity = downward flick → shrink one detent
- negative velocity = upward flick → grow one detent
- magnitudes equal to the threshold do **not** nudge — strict `>` /
  `<` only — so the threshold edge defers to snap-to-nearest
- snap-to-nearest by absolute distance to the released height when
  neither flick branch fires

Velocity units differ by platform; the resolver threshold is
calibrated per-platform so the same physical flick crosses it on
every device:

| Platform | Units | Threshold |
|---|---|---|
| iOS | UIKit points / second | 600 (`velocityThreshold`) |
| Android | raw pixels / second (Compose `draggable`) | 1 200 (`VELOCITY_THRESHOLD`) |
| **Web** | **CSS pixels / second (pointer events)** | **600 (`MAP_LIST_HYBRID_VELOCITY_THRESHOLD`)** |

(600 pt/s ≈ 1 200 px/s on a 2x retina iOS device; CSS pixels are
density-independent like iOS points, hence the 600 match.)

## Pin↔list selection sync

Same contract as iOS + Android: shell fires `onPinTap(id: string)`
when a pin is tapped; consumer updates its own selection state and
snaps `detent` to `'standard'`. The active pin draws a 1.6s dual-
halo pulse on web (suppressed under `prefers-reduced-motion: reduce`;
static ring instead).

## Test tag parity (root container)

All three platforms expose the same set of test identifiers on the
shell-owned wrappers:

| `data-testid` | Wraps |
|---|---|
| `mapListHybridShell` | full canvas root |
| `mapListHybridMap` | the live map layer (also a `mapListHybridMapSkeleton` while the dynamic import loads) |
| `mapListHybridTopPill` | top-pill slot wrapper |
| `mapListHybridChips` | category-chips slot wrapper |
| `mapListHybridMapControls` | map-controls slot wrapper |
| `mapListHybridSheet` | the rounded bottom-sheet container |
| `mapListHybridSheetHeader` | sheet-header slot wrapper |
| `mapListHybridSheetBody` | sheet-body slot wrapper |
| `mapListHybridPin_<id>` | per-pin marker (tags via the leaflet `DivIcon` HTML) |

iOS uses `accessibilityIdentifier`; Android uses `Modifier.testTag`;
web uses `data-testid`. Slot contents carry their own consumer-
supplied identifiers.

## Lifecycle states

Every fetchable map-list surface ships **four**:

1. **`loading`** — map skeleton + sheet shimmer.
2. **`empty`** — "Nothing nearby" + radius-widening CTA in the sheet.
3. **`loaded`** — pins on the map + carousel / list in the sheet.
4. **`error`** — `<ErrorBanner>` with `Retry` wired to refresh
   inside the sheet header.

## See it rendered

Local dev: navigate to `/map-list-hybrid-preview` after
`pnpm -F @pantopus/web dev`. Every detent rendered side-by-side
with live Mapbox tiles + sample pins so the designer can sanity-
check the visual contract.

## Snapshot baselines

Web visual-regression baselines live under
`frontend/apps/web/tests/visual-regression/__screenshots__/t6-map-*`
(playwright).

Design-reference PNGs (the visual contract) live alongside the iOS
baselines under `docs/screenshots/__snapshots__/t6/map-list-hybrid.png`.

## Adding a new map-list consumer

1. **Compose** a hook / component that exposes the map-list state
   (`MapPin[]`, list rows, selected pin id) + four lifecycle
   states.
2. **Wrap** the page in `<MapListHybridShell …>` and supply the
   6 slots. The shell handles the map layer + detent gestures.
3. **Wire** `onPinTap(id)` to update the selected state and snap
   `detent` to `'standard'`.
4. **Tests.** Component tests via React Testing Library cover
   load → loaded / empty / error + selection sync.
5. **Parity audit + wiring audit** rows for the new consumer.

— Pantopus T6.6a
