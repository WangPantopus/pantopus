# MailItemDetail · web

The shared shell every mail-item-detail screen on web ships on top
of. Introduced in T6.5a (P19) to express the **A17 mailbox
archetype** — the 8-slot composition that the four T6 mailbox
variants (Generic / Booklet / Certified / Community) all render
through.

| File | Role |
|---|---|
| `MailItemDetailShell.tsx` | The shell — wraps the 8 slots in a flex column with sticky action bar; renders sticky bottom CTAs when non-empty. |
| `types.ts` | `MailItemDetailUiState`, `MailTopBarConfig`, `AIElfStripContent`, `AttachmentsRowContent`, `Attachment` union. |
| `ChainOfCustodyTimeline.tsx` | T6.5c-lifted shared vertical timeline. Re-used by Certified mail and (future) package-delivery / Community RSVP timelines. |

## When to use this shell

Reuse the shell whenever the design is:

> a vertically scrolling mail-item or document detail with up to 8
> stacked sections — top nav, hero card, AI elf strip, key facts,
> body, attachments, sender card, sticky actions.

T6.5b–T6.5d ship 4 mail variants on this shell:
- **A17.1 Generic**, **A17.2 Booklet**, **A17.3 Certified**,
  **A17.4 Community**.

**If a future mail variant doesn't fit the 8 slots**, propose an
additive extension here first (a new optional slot, a new
`Attachment` discriminated union case, an additional
`MailTopBarConfig.overflow` item) — never widen an existing slot in
a way that breaks v1 callers.

## Anatomy

```
┌────────────────────────────────────────────────┐
│ ◀  ●  CATEGORY  ★  ⋮                           │  TopNav    (required)
├────────────────────────────────────────────────┤
│ ╭──────── Hero card ────────────────────╮      │  Hero      (required slot)
│ │ accent strip · sender · title · excerpt│      │
│ ╰────────────────────────────────────────╯     │
├────────────────────────────────────────────────┤
│ ✨ AI elf · summary · • point • point  · redo │  AIElfStrip(opt)
├────────────────────────────────────────────────┤
│ ╭──── Key facts ──────────────────────╮       │  KeyFacts (required slot)
│ ╰─────────────────────────────────────╯       │
├────────────────────────────────────────────────┤
│ ╭──── Body ────────────────────────────╮      │  Body     (required slot)
│ ╰───────────────────────────────────────╯     │
├────────────────────────────────────────────────┤
│ [PDF] [IMG] [VID] [AUD] [URL]                  │  Attachments (opt)
├────────────────────────────────────────────────┤
│ ╭──── Sender card ─────────────────────╮      │  Sender   (required slot)
│ ╰───────────────────────────────────────╯     │
├────────────────────────────────────────────────┤
│ [ Acknowledge receipt ]   [ Forward ]          │  Actions  (sticky bottom)
└────────────────────────────────────────────────┘
```

## Slot contract

Slots in render order (top → bottom):

| # | Slot | Type | Role |
|---|---|---|---|
| 1 | `nav` | `MailTopBarConfig` (required) | back callback + eyebrow trust dot (`'verified' \| 'neutral' \| 'warning'`) + optional bookmark/pin + overflow menu list (Forward / Archive / Mark unread / Delete / Report — variants add or drop items). |
| 2 | `hero` | `ReactNode` (required) | accent strip + sender overline + title + excerpt. |
| 3 | `aiElf` | `AIElfStripContent?` (optional, shell-rendered) | sparkles disc + headline + summary paragraph + bullet list + optional trailing badge + optional redo. |
| 4 | `keyFacts` | `ReactNode` (required) | sunken-bg key-value panel. |
| 5 | `body` | `ReactNode` (required) | full-text body card or replacement (booklet pager, ceremonial paper). |
| 6 | `attachments` | `AttachmentsRowContent?` (optional, shell-rendered) | list of `Attachment` items with per-kind 36×44 tile colours. |
| 7 | `sender` | `ReactNode` (required) | sender card. |
| 8 | `actions` | `ReactNode`, sticky bottom | bottom action button row. Auto-hidden when `null`. |

## Test tag parity (root container)

Both platforms + web use the same identifier — `mailItemDetailShell`
— plus child tags (see the iOS README for the full list). The same
string lives on iOS (`accessibilityIdentifier`), Android
(`Modifier.testTag`), and web (`data-testid`) so cross-platform UI
tests can mirror.

## Lifecycle states

Every fetchable surface ships **four**:

1. **`loading`** — shimmer skeleton mirroring the loaded geometry.
2. **`empty`** — currently unreachable for mail detail; reserved.
3. **`loaded`** — content rendered through the 8-slot layout.
4. **`error`** — `<ErrorBanner>` with `Retry` wired to refresh.

## See it rendered

Local dev: navigate to `/mail-item-detail-preview` after
`pnpm -F @pantopus/web dev`. Renders three example configurations
(every slot supplied · required slots only · nil-skipping) so the
designer can sanity-check spacing, trust dot, AI elf, and
attachments tiles.

## Snapshot baselines

Web visual-regression baselines live under
`frontend/apps/web/tests/visual-regression/__screenshots__/t6-mail-*`
(playwright). Run with `pnpm -F @pantopus/web exec playwright test`.

Design-reference PNGs (the visual contract) live alongside the iOS
baselines under `docs/screenshots/__snapshots__/t6/`.

## Adding a new mail variant

1. **Walk the design.** Confirm the variant fits the 8 slots.
2. **Compose** a `MailDetailContent.<variant>Detail` discriminated-
   union case that decodes from the `mail.object` JSONB on the wire.
3. **Build** a `<Variant>Detail` component that supplies the 6
   generic slots; reuse the shell's AI-elf-strip + attachments
   rendering.
4. **Wire** the variant dispatch in the existing mail detail page;
   no new endpoint surface unless the variant has its own backend
   route.
5. **Tests.** Component tests via React Testing Library cover
   variant dispatch + load → loaded / empty / error.
6. **Parity audit + wiring audit** rows for the new variant.

## Cross-reference — A17 catalogue

The 4 mail variants this shell powers:

| Variant | Web page | Backend object decoder |
|---|---|---|
| A17.1 Generic | `app/(app)/app/mailbox/[id]/page.tsx` (default) | `GenericDetailDto` |
| A17.2 Booklet | (same page + `BookletPager` body slot) | `BookletDetailDto` |
| A17.3 Certified | (same page + Certified hero badge + custody timeline) | `CertifiedDetailDto` |
| A17.4 Community | (same page + community cards) | `CommunityDetailDto` |

— Pantopus T6.5a
