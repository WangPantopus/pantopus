# `<ListOfRowsShell />` · web

The shared shell every list-of-rows page on the web ships on top of. The
web mirror of the iOS / Android `ListOfRows` archetype.

| File | Role |
|---|---|
| `ListOfRowsShell.tsx` | The shell component — top bar, optional search / chip strip / tab strip / banner, scroll body, FAB, four lifecycle states. |
| `RowCard.tsx` | One row's render. Maps `RowModel` → JSX. |
| `TabStrip.tsx` | The tabs row (mobile shrink-to-fit; desktop equal-width). |
| `LoadingRows.tsx`, `EmptyState.tsx`, `ErrorBanner.tsx`, `FabButton.tsx` | Lifecycle / chrome pieces. |
| `types.ts` | `ListOfRowsShellProps`, `RowModel`, `RowLeading`, `RowTrailing`, `RowChip`, `RowFooter`, `RowEngagement`, `RowSection`, `SectionStyle`, `RowHighlight`. |
| `index.ts` | Single barrel re-export. |

T5 added 10 row shapes, 4 trailing variants, a leading-thumbnail size, a
bidder-stack primitive, a chip-strip slot, a banner slot, a hero-card slot,
two FAB variants, and the `primary25` background tint. Every extension is
**additive** — the original Notifications v1 / Mailbox call sites compile
untouched.

## When to use this shell

Reuse the shell whenever the design is:

> a vertically scrolling list of cells, with optional chrome
> (top bar, search, tabs, chip strip, banner, FAB) above it.

T1–T5 ship 13 screens on this shell. Tier 5 alone added 12. The 13th screen
should use this shell too — keep going until a design pushes us past what
the contract can express **without** breaking an existing call site. **If
the row contract is too narrow, propose an additive extension** (a new
`RowLeading` discriminator, a new `RowTrailing` discriminator, a new
optional `RowModel` field with an undefined default) — never widen an
existing case in a way that breaks v1 callers.

## Anatomy

```
┌───────────────────────────────────────┐
│ ◀  Title              Action / icon   │  TopBar          (52px)
├───────────────────────────────────────┤
│  🔍  Search…                          │  SearchBar (opt) (44px)
├───────────────────────────────────────┤
│ [All] [Tab] [Tab]                      │  TabStrip  (opt) (44px)
│ or                                    │   — OR —
│ • Chip · Chip · Chip                  │  ChipStrip (opt) (44px)
├───────────────────────────────────────┤
│ ┌──────── BannerConfig ─────────────┐ │  Banner    (opt)
│ └───────────────────────────────────┘ │
├───────────────────────────────────────┤
│  ┌────── Row ───────────────────────┐ │
│  │ leading  title              tail │ │
│  │          subtitle                │ │
│  │          chips · metaTail        │ │
│  │          ─── inline footer ───── │ │
│  └──────────────────────────────────┘ │
│  ┌────── Row ───────────────────────┐ │
│  └──────────────────────────────────┘ │
│                                       │
│                                  ⊕    │  FabAction (opt)
└───────────────────────────────────────┘
```

## RowModel contract

```ts
type RowModel = {
  // Identity
  id: string;                              // stable, dedup, scroll-restore
  template: RowTemplate;                   // 'statusChip' | 'fileChevron' | 'avatarKebab'

  // Slots
  leading?: RowLeading;
    // { kind: 'none' } | { kind: 'icon' } | { kind: 'avatar' }
    // | { kind: 'typeIcon' } | { kind: 'categoryGradientIcon' }
    // | { kind: 'avatarWithBadge' } | { kind: 'thumbnail' }
    // | { kind: 'bidderStack' }
  trailing?: RowTrailing;
    // { kind: 'none' } | { kind: 'chevron' } | { kind: 'kebab' }
    // | { kind: 'statusChip' } | { kind: 'circularAction' }
    // | { kind: 'verticalActions' } | { kind: 'priceStack' }
    // | { kind: 'amountWithChip' }

  // Content
  title?: string;
  subtitle?: string;
  body?: string;                           // 2-line meta block under title
  bodyEmphasis?: 'secondary' | 'primary';  // T5.3.3 — My posts uses 'primary'

  // Chips / meta
  headerChips?: RowChip[];                 // T5.3.3 — above body (My posts intent)
  chips?: RowChip[];                       // status / category chips on meta line
  metaTail?: string;                       // "· 3 others bid"
  inlineChip?: RowChip;                    // T5.2.1 — chip on title line (Pets species)

  // Highlight / footer / engagement
  highlight?: 'unread' | 'leading' | 'archived' | 'muted';
  footer?: RowFooter;                      // 1–3 buttons (CompactButton.footer)
  engagement?: RowEngagement;              // T5.3.3 — comment + like counters + Edit
  note?: string;                           // T5.3.4 — italic quote line

  // Callbacks
  onTap?: () => void;
  onSecondary?: () => void;                // kebab → menu
};
```

## Leading variants

| Discriminator | Geometry | Used by |
|---|---|---|
| `none` | 0px — no leading column | My pulse (header-chip + body only) |
| `icon` | 40px icon disk, no background | legacy v1 |
| `typeIcon` | 40px circular tile with tinted fill | Notifications · Bills |
| `categoryGradientIcon` | 40px circular tile with two-stop gradient + white icon | My bids · My tasks · Offers · Discover hub Gigs |
| `avatar` | 40px avatar with identity-pillar ring | Connections v1, Mailbox |
| `avatarWithBadge` | 36 / 40 / 44px avatar with verified-badge overlay | Connections · Discover hub People · Listing offers · Review claims |
| `thumbnail` | 56px or 64px rounded-square | Offers · Pets · Listing offers hero |
| `bidderStack` | 22px overlapping mini-avatars + "+N" tile | My tasks V2 |

## Trailing variants

| Discriminator | Geometry | Used by |
|---|---|---|
| `none` | none | when the row is fully self-contained |
| `chevron` | 16px right-chevron | drill-down |
| `kebab` | 36px more-vertical icon button → `onSecondary` | My pulse · Pets |
| `statusChip` | a single chip aligned trailing | v1 |
| `circularAction` | 38px round button | Connections (message CTA) |
| `verticalActions` | 30px primary + 28px ghost stacked | Connections pending tab (Accept / Ignore) |
| `priceStack` | right-aligned $ + sub-line | My bids · My tasks · Offers · Listing offers · Discover hub Gigs / Listings |
| `amountWithChip` | $ stacked vertically over a chip | Bills |

## RowSection extension

```ts
type RowSection = {
  id: string;
  header?: string;                  // existing
  count?: number;                   // T5.4.1 — bumped count under the title
  onSeeAll?: () => void;            // T5.4.1 — trailing CTA
  style?: 'plain' | 'card';         // 'card' — hairline rows in one rounded card
  rows: RowModel[];
};
```

`style: 'card'` renders the section's rows inside a single rounded card
with hairline dividers — no inter-row gap. Discover hub and Discover
businesses are the two consumers today.

## Chrome slots

| Slot | Type | Geometry |
|---|---|---|
| `title` | `string` | required, centred |
| `topBarAction` | `TopBarAction \| undefined` | trailing icon-or-text button (Notifications "Mark all read", Discover businesses sliders) |
| `searchBar` | `SearchBarConfig \| undefined` | 44px sunken pill above tabs (Connections, Discover businesses) |
| `tabs` + `selectedTab` | `Tab[]` + `string` | mobile shrink-to-fit; desktop equal-width |
| `chipStrip` | `ChipStripConfig \| undefined` | alternative to `tabs` — horizontally scrollable filter pills (Discover hub, Discover businesses) |
| `banner` | `BannerConfig \| undefined` | primary / amber tinted summary card above the first row (My bids, My tasks, Offers, Review claims) |
| `listingContext` | `ListingContextConfig \| undefined` | sticky hero card with image + meta + ask price (Listing offers only) |
| `fab` | `FabAction \| undefined` | 56 / 52 / 48px — see `FabVariant` below |

## FAB variants

| Variant | Diameter | Use |
|---|---|---|
| `'canonicalCreate'` | 56px round | the screen's primary create action (My tasks V2 "Post a task") |
| `'secondaryCreate'` | 52px round | non-canonical create (My pulse, Connections, Bills, Pets) |
| `{ variant: 'extendedNav', label }` | 48px pill | navigation FAB — "Browse tasks" on My bids |

## Lifecycle states

Every fetchable surface ships **four**:

1. **`{ kind: 'loading' }`** — `<LoadingRows />` shimmer that mirrors the
   loaded geometry.
2. **`{ kind: 'empty', ... }`** — `<EmptyState />` with icon + headline +
   body + optional CTA. The Notifications empty-Unread CTA re-keys the
   tab to `All` in place — no route push (per F2 in the buildout plan).
3. **`{ kind: 'loaded', sections, hasMore }`** — content.
4. **`{ kind: 'error', message }`** — `<ErrorBanner />` with `Retry`
   wired to `onRefresh`.

## See it rendered

The web has a `/list-of-rows-preview` route at
`src/app/(internal)/list-of-rows-preview/page.tsx`. It renders every
row variant against the design tokens. Canonical visual contract that
all three platforms target.

```sh
pnpm -F @pantopus/web dev
open http://localhost:3000/list-of-rows-preview
```

Every leading / trailing / chip / footer / highlight variant renders
once with a label.

## Snapshot baselines

Design-reference PNGs (the visual contract):
`frontend/apps/web/tests/visual-regression/__snapshots__/t5/<screen>-web.png` —
12 of them, one per T5 screen. Generated by the static HTML harness at
`tools/t5-screenshots/` (kept in `/tmp` — regenerable from the design
package).

Platform-rendered baselines via Playwright:
`frontend/apps/web/tests/visual-regression/__snapshots__/t5/platform/<screen>.png`
— captured against `/list-of-rows-preview` and per-page routes by the
visual-regression suite. Drift is caught by
`tests/visual-regression/t5-screens.spec.ts`. Run with:

```sh
pnpm -F @pantopus/web exec playwright test tests/visual-regression
```

Update baselines with `--update-snapshots`.

## Adding the 13th screen

1. **Walk the design.** Identify which row shape it needs. If every cell
   maps to an existing `RowLeading` × `RowTrailing` combination, you're
   done — skip to step 4.
2. **If the design needs a new row variant**, propose an additive
   extension to `RowLeading` or `RowTrailing` in `types.ts` **first**,
   then use it from the feature. Existing call sites must keep compiling.
3. **If the design needs a new chrome slot** (e.g. a footer bar above the
   tab strip), discuss it before extending the shell. Two screens of demand
   is the bar for adding a slot.
4. **Wire the page.** New route under `src/app/(app)/app/<route>/page.tsx`.
   The page renders `<ListOfRowsShell />` and passes its query-driven
   state via `@tanstack/react-query`. No new state libraries.
5. **Wire the tests.** Add a `<feature>-page.test.tsx` covering the
   loading / empty / loaded / error projections, plus an entry in
   `tests/visual-regression/t5-screens.spec.ts` so the snapshot baseline
   lands in CI.
6. **Wire the parity audit** at `docs/mobile-parity-audit.md` and the
   wiring audit at `docs/mobile-wiring-audit.md`.

The longer doc-of-record for the archetype evolution lives at
`docs/t5-buildout-plan.md`; the prompt-style notes are at
`docs/mobile/pantopus-t5-notes.md`.

— Pantopus T5
