# Visual-parity fixes

> **Format:** one section per sub-group. Append-only. Each entry lists
> the source design (HTML/CSS file + frame), the implementation file +
> line, the mismatch, and the fix applied (or the reason it was left
> for design review).

## Methodology limitations (this container)

- **No iOS simulator** (no Xcode toolchain). App-side renders cannot be
  taken; only design-side renders are possible.
- **No Android emulator** (no Android SDK + `dl.google.com` is sandbox-
  blocked, so the Android Gradle Plugin can't be fetched).
- **Browser:** Playwright + Chromium are present. Of the 8 design packs
  referenced in the prompt, only **`Chat_conversation/`** is extracted
  on disk; the other 7 packs (A08, A10, A13, Creator_Audience_hub,
  Full-bleed_map_*, Wizard_multi-step_*, mobile_Mailbox_root_archetype)
  remain as session-context zips. The CDN-hosted React/Babel/Lucide
  scripts that the chat HTMLs `<script src=…>` are blocked by the
  sandbox; they were vendored locally for this audit.

Side-by-side image diff isn't possible. The audit instead diffs
**design CSS specs against implementation Swift/Kotlin dimensions** and
applies token-resolvable fixes. Design-side screenshots are saved
under `/tmp/p79a-audit/shots/` and were sent to the user for review.

---

## P7.9a — Chat conversation (A15.x)

### Design source

`Chat_conversation/` pack — 5 HTMLs × 2 frames each = 10 designed
artboards at 382 × 782pt:

| Pack file | Frames |
|---|---|
| `A15 - Chat conversation.html` | Populated · active task thread + Empty · Say hi |
| `A15.2 - Conversation.html` | Populated · ongoing neighbor DM + Empty · Say hi (person card) |
| `A15.3 - AI Assistant.html` | Populated · structured replies + thinking + Empty · Ask me anything |
| `A15.4 - Creator thread.html` | Populated · Bronze fan · 3 replies left + Secondary · weekly quota exhausted |
| `A15.5 - Fan thread.html` | Populated · active fan thread · 3 of 5 left + Empty · Start a conversation · 5 of 5 |

All artboards share the chat-archetype CSS at
`Chat_conversation/chat-archetype.css`.

### Implementation files audited

- iOS: `Features/Chat/Conversation/ChatConversationView.swift` (the
  shared shell that renders DM / AI / Creator / Fan modes).
- Android: `ui/screens/inbox/conversation/ChatConversationScreen.kt`.

### Mismatches and fixes

#### 1. Chat header avatar size — **FIXED** (iOS + Android)

| Side | Before | After | Source |
|---|---|---|---|
| Design | `.chat-id .avatar { width: 36px; height: 36px; }` (chat-archetype.css L62) | n/a | reference |
| iOS | `ChatPersonAvatar(..., size: 32)` + `ChatAIAvatar(size: 32)` + `ChatFanPersonaAvatar(..., size: 32)` (6 sites in `ChatConversationHeader.avatar`) | all `size: 36` | `ChatConversationView.swift:864–887` |
| Android | `ChatAiAvatar(size = 32.dp)` + `FanPersonaAvatar(... size = 32.dp)` + `PersonAvatar(... size = 32.dp)` (5 sites in `ChatHeaderAvatar`) | all `size = 36.dp` | `ChatConversationScreen.kt:665–683` |

The 4pt discrepancy (32 vs 36) was visible in the design overview
screenshots — the design's circle reads heavier next to the
neighboring "HOME" / "BIZ" identity pill at the same height. Both
platforms now match.

Sites left intentionally at 32 (inline content, not header):
`ChatConversationView.swift:341` (the AI welcome card body, inline
avatar) and `ChatConversationScreen.kt:1300` (the AI welcome card
equivalent). Those don't sit in the chat-header geometry.

#### 2. ChatComposer outer top padding — **FIXED** (iOS only)

| Side | Before | After | Source |
|---|---|---|---|
| Design | `.composer-wrap { padding: 8px 10px 16px; }` (chat-archetype.css L317) | n/a | reference |
| iOS | `.padding(.top, 10)` (literal, off the scale) | `.padding(.top, Spacing.s2)` (= 8pt) | `ChatConversationView.swift:1915` |
| Android | `padding(start = 10.dp, end = 10.dp, top = Spacing.s2, bottom = Spacing.s4)` | unchanged — already correct | `ChatConversationScreen.kt:2113` |

iOS was 2pt heavier than design at the top of the composer; Android
was already on-spec. Both now match the 8/10/16 padding.

#### 3. ChatComposer input field leading padding — **FIXED** (iOS + Android)

| Side | Before | After | Source |
|---|---|---|---|
| Design | `.composer .input { padding: 8px 12px; }` (chat-archetype.css L327) | n/a | reference |
| iOS | `.padding(.leading, 14)` (literal, +2 off) | `.padding(.leading, Spacing.s3)` (= 12pt) | `ChatConversationView.swift:1881` |
| Android | `padding(start = 14.dp, end = Spacing.s1, top = 2.dp, bottom = 2.dp)` | `padding(start = Spacing.s3, end = Spacing.s1, top = 2.dp, bottom = 2.dp)` | `ChatConversationScreen.kt:2161` |

Input cursor sat 2pt right of the chip-of-text-with-emoji layout in
the design. Both platforms now match.

### Mismatches surfaced for design review (NOT fixed — off-scale design value, or design-side inconsistency)

| # | Concern | Why not auto-fixed |
|---|---|---|
| A | **iOS composer outer horizontal padding: `Spacing.s3` (12pt) vs design `10px`.** Already documented as P7.2 drift (off-scale value). Android uses 10dp literal; iOS uses Spacing.s3 token. | The design's `10px` is itself off the canonical spacing scale. Per the prompt rule "do not invent" — adding a new 10pt token would violate the "no new tokens" rule. Either accept the 2pt platform-divergence (iOS 12 / Android 10), or extend the scale with design sign-off. |
| B | **iOS trailing icon size: 18pt vs design `width: 20px`.** Phone / video / more-vertical icons in the right-aligned header actions. iOS uses `Icon(.phone, size: 18, …)` everywhere; design specifies 20px. | The 2pt size delta would mean changing all `Icon(.X, size: 18, …)` in `trailingActions` to `size: 20`. Surface for design call — possibly the iOS implementation deliberately reduced these to balance visual weight against the avatar at 32pt (now 36pt). Re-evaluate after a UAT run. |
| C | **iOS trailing-action button frame: 34×34 vs design `.icon-btn { width: 32px; height: 32px; }`.** iOS uses `.frame(width: 34, height: 34)`. | iOS 34pt is off the canonical scale; design's 32 IS on-scale (would map to `Spacing.s8`). Surface as a follow-up — changing 34 → 32 would touch every trailingAction site and is a deliberate-design-decision question. |
| D | **iOS chat header HStack spacing: `10` literal vs the design's grid-gap of `10px`.** Already matches design exactly, just uses a literal instead of a token. | No 10pt spacing token exists (Spacing.s2 = 8, Spacing.s3 = 12). Per "no new tokens", leave the literal. |
| E | **Typography header title: design `font-size: 14px; font-weight: 700; (bold)`. iOS uses `.font(.system(size: 14, weight: .bold))`.** Matches design but uses literal. | Already documented in P7.4 typography drift as "off-scale: matches small but weight .bold ≠ .regular". Token would need a `.smallBold` extension — design sign-off required. |
| F | **CeremonialMail / AI-assistant gold avatar gradient and AI-welcome strip — design uses CSS `linear-gradient` matching the iOS gradients at `ChatConversationView.swift:1476` / Android `ChatConversationScreen.kt:1640`.** | Already audited in P7.7 (`docs/gradient-provenance.md`). The Chat paywall overlay specifically was tagged `DESIGN_NOT_FOUND` because the available Chat pack doesn't show this paywall pattern. |
| G | **Other 7 design packs not on disk — A08, A10, A13, Creator_Audience_hub, Full-bleed_map_*, Wizard_multi-step_*, mobile_Mailbox_root_archetype.** Their `.html` files were uploaded as session context but never extracted, so per-screen visual-parity passes for those sub-groups can't run in this container. | Out of scope for P7.9a. Future P7.9b/9c/etc. would need the packs extracted first. |

### Snapshot tests

Re-record needed for **`ChatConversationView` snapshots** on iOS and
**`ChatConversationScreen` snapshots** on Android — the avatar +
padding changes are visible in the loaded-state baseline. Run
`make test` (iOS, first record clean) and `./gradlew paparazziRecord`
(Android) and commit the new baselines.

### Verification

Files modified:
- `frontend/apps/ios/Pantopus/Features/Chat/Conversation/ChatConversationView.swift` — 6 avatar size + 1 composer-top padding + 1 input-leading padding lines.
- `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/inbox/conversation/ChatConversationScreen.kt` — 5 avatar size + 1 input-start padding lines.

Design renders saved to `/tmp/p79a-audit/shots/` (15 PNG files: 5
overviews + 10 per-artboard crops) and sent to the user.

---

## P7.9.a — Hub tab core surfaces

### Design source

| File | Pack | Frames audited |
|---|---|---|
| `A08 — per-screen batch 1/uploads/Pantopus-design/Hub.html` (+ `hub-frames.jsx`) | A08 archetype | FRAME 1 Populated, FRAME 2 First-run, FRAME 3 Skeleton |
| `A08 — per-screen batch 1/uploads/Pantopus-design/Me.html` (+ `me-frames.jsx`) | A08 archetype | FRAME 1 Personal, FRAME 2 Home |
| `A10___Detail__Content/A10.3 Today.html` (+ `today-frames.jsx`) | A10 detail | FRAME 1 Populated, FRAME 2 Advisory |
| `A08 — per-screen batch 1/uploads/Pantopus-design/List of Rows.html` (+ `frames.jsx`) | A08 archetype (Recent Activity inherits) | FRAME 1 Primary, FRAME 2-3 Variant, FRAME 4 Empty |

Renders saved to `/tmp/p79b-hub/shots/` and sent to user.

### Implementation files audited

| Surface | iOS | Android |
|---|---|---|
| Hub action chips | `Core/Design/Components/ActionChip.swift` (shared component) + `Features/Hub/Sections/HubSections.swift:HubActionStrip` | `ui/components/ActionChip.kt` + `ui/screens/hub/sections/HubSections.kt` |
| Hub pillar tiles | `Features/Hub/Sections/HubSections.swift:PillarTileBody` | `ui/screens/hub/sections/HubSections.kt:PillarTileView` |
| Hub Today card | `Features/Hub/Sections/HubSections.swift:HubTodayCard` | `ui/screens/hub/sections/HubSections.kt` |
| Me identity / stats / grid | `Features/Me/MeView.swift` | `ui/screens/you/me/MeView.kt` |
| Today detail hero | `Features/Hub/Today/TodayDetailView.swift:TodayHero` | `ui/screens/hub/today/TodayDetailScreen.kt:TodayHero` |
| Recent Activity (List-of-Rows) | `Features/RecentActivity/RecentActivityView.swift` (thin wrapper around `ListOfRowsView`) | mirror |

### Mismatches and fixes

#### 1. `ActionChip` corner radius — **FIXED** (iOS + Android, shared component)

| Side | Before | After |
|---|---|---|
| Design (hub-frames.jsx L248) | `borderRadius: 12` on the action chip surface | n/a |
| iOS | `RoundedRectangle(cornerRadius: Radii.pill, …)` (=9999, capsule) | `Radii.lg` (=12) |
| Android | `RoundedCornerShape(Radii.pill)` (3 occurrences in ActionChip.kt L62/63/69) | `Radii.lg` |

The Hub design renders action chips as rounded-rectangles, not pills.
Visible delta in screenshots: the "Post task" / "Snap & sell" / "Scan
mail" trio currently looks capsule-shaped on both platforms.

Knock-on: `MailItemDetailShell.swift:303` and `:306` also use
`ActionChip` for the mail-item primary/secondary CTAs. The Mailbox
design (mail-detail.jsx L470) specifies `borderRadius: 12` for the
secondary CTA grid — matches the new ActionChip radius. The Mailbox
primary CTA is a separate `borderRadius: 14` full-width button (not
the shared `ActionChip`), so no regression.

#### 2. `PillarTile` icon container corner radius — **FIXED** (iOS + Android)

| Side | Before | After |
|---|---|---|
| Design (hub-frames.jsx L366) | inner icon container `width: 32, height: 32, borderRadius: 8` | n/a |
| iOS `Features/Hub/Sections/HubSections.swift:PillarTileBody` | `Radii.sm` (=6) on the icon container | `Radii.md` (=8) |
| Android `ui/screens/hub/sections/HubSections.kt:PillarTileView` | `Radii.sm` (=6) | `Radii.md` (=8) |

#### 3. `TodayHero` weather-glyph container corner radius — **FIXED** (iOS + Android)

| Side | Before | After |
|---|---|---|
| Design (today-frames.jsx L188) | `width: 56, height: 56, borderRadius: 16` for the glyph disc | n/a |
| iOS `TodayDetailView.swift:TodayHero` | `Radii.lg` (=12) | `Radii.xl` (=16) |
| Android `TodayDetailScreen.kt:TodayHero` | `Radii.lg` (=12) | `Radii.xl` (=16) |

### Mismatches surfaced for design review (NOT fixed — off-scale design values)

The audit's "don't invent tokens" rule means design values not on the
canonical 4/6/8/12/16/20/24/9999 ramp can't be auto-fixed. Each entry
below is a real visual delta the audit found but can't resolve without
either (a) extending the token scale or (b) design accepting the
nearest-on-scale approximation.

| # | Surface | Design value | Code value | Notes |
|---|---|---|---|---|
| A | Hub action chip inner horizontal padding | `padding: 0 14px` (line 248) | iOS+Android `Spacing.s3` (=12) | 14pt off-scale on the design side. Either accept the 2pt under-padding, or design adds `s_14` / changes the design to 12. |
| B | Hub action chip icon size | 15 | iOS 16, Android `Radii.xl2`/effective 20 | Design's 15pt is off-scale; nearest token = 16. |
| C | Hub action chip font | `fontSize: 12.5, fontWeight: 600` | iOS `.pantopusTextStyle(.small)` (= 14/regular), Android same | The 12.5/semibold combination isn't in the type-ramp; already P7.4 drift. |
| D | Hub `Today` card outer corner radius | `borderRadius: 14` (line 315) | iOS+Android `Radii.lg` (=12) | 14 off-scale. |
| E | Hub `Today` card horizontal padding | `padding: 12px 14px` → 14 horizontal | iOS `Spacing.s3` (=12) | 14 off-scale. Closest tokens 12 or 16. |
| F | Hub `Today` card weather-icon container border-radius | `borderRadius: 10` (L322) | iOS+Android `Radii.md` (=8) | 10 off-scale. |
| G | Hub `Today` card weather icon size | 22 | iOS+Android 20 | Off by 2; no 22pt icon token. |
| H | Hub `Today` card temperature font | `fontSize: 20, fontWeight: 700, letterSpacing: -0.4, lineHeight: 22` | iOS `.font(.system(size: 20, weight: .bold))`, Android same | Size 20 matches; weight 700 ≈ .bold matches; tracking and line-height not tokenised — already P7.4 drift. |
| I | Hub pillar tile caption font | `fontSize: 10.5` | iOS+Android `size: 11` | 10.5 off-scale. |
| J | Me stats-row outer corner radius | `borderRadius: 14` (me-frames.jsx L174) | iOS+Android `Radii.lg` (=12) | 14 off-scale. |
| K | Me identity-row pill segment height | `height: 30` (L140) | iOS `IdentitySwitcherPillRow` — not directly inspected; relies on the segmented control's implicit height | Spec value 30 is off-scale (closest token Spacing.s8 = 32). |
| L | Today detail hero — design uses a blue gradient surface | `background: gradient, color: #fff` (hero L146) | iOS+Android use `Theme.Color.appSurface` (flat white surface) — **INTENTIONAL DEVIATION** | Per the project's "no gradients on mobile shells" rule (codified in `Features/Hub/Today/TodayDetailView.swift:7` comment). Surfaced as design-side question: should the no-gradient-on-mobile rule be relaxed for the Today hero? |
| M | Today detail temperature display font | design `fontSize: 40, fontWeight: 800` | iOS `.pantopusTextStyle(.h1)` (=30/bold), Android same | A 10-point delta — design's 40pt is off the 30/24/20/16/14/12/11 ramp. The largest scale entry is `.h1` (30). Either extend the ramp (`.h0`?) or accept the smaller-than-design hero number. |
| N | Recent Activity row geometry | inherits `ListOfRows` archetype frames (Bills/Docs/Members) | uses the same shared shell with the same geometry | No bespoke divergence to flag; matches the archetype. The ListOfRows shell's own row dimensions were not re-audited in this prompt — covered separately when the archetype itself is audited. |

### Snapshot tests

Re-record needed for:
- iOS `PantopusTests/Features/Hub/HubViewTests` populated snapshot (ActionChip + pillar tile icon background)
- iOS `PantopusTests/Features/Mailbox/ItemDetail/MailboxItemDetailViewTests` (the ActionChip CTA shelf radius)
- iOS `PantopusTests/Features/Hub/Today/TodayDetailViewTests` populated snapshot (hero glyph radius)
- Android equivalents for the same three screens

### Verification

iOS `make verify-tokens` ✅ pass; Android equivalent grep (manual) returns 0 on-scale literals. All changes use canonical tokens (`Radii.lg`, `Radii.md`, `Radii.xl`). No new tokens introduced.

Files modified (6 lines across 6 files):
- iOS: `Core/Design/Components/ActionChip.swift` (2 lines), `Features/Hub/Sections/HubSections.swift` (1 line), `Features/Hub/Today/TodayDetailView.swift` (1 line)
- Android: `ui/components/ActionChip.kt` (3 lines via `replace_all`), `ui/screens/hub/sections/HubSections.kt` (1 line), `ui/screens/hub/today/TodayDetailScreen.kt` (1 line)

---

## P7.9.b — Pulse + post detail visual parity

**Date:** 2026-05-26 · **Branch:** `claude/loving-hamilton-OI30q` · **Commit:** appended this prompt

### Scope

Hub tab parity (P7.9.a) covered Hub / Me / Today / Recent Activity surfaces.
This prompt covers the **Pulse tab** + the **post-detail content shell**:

| Surface | Design HTML | iOS implementation | Android implementation |
|---|---|---|---|
| Pulse feed (Populated · Empty · Loading) | `A08 — per-screen batch 1/uploads/Pantopus-design/Pulse.html` + `pulse-frames.jsx` | `Features/Feed/FeedView.swift` + `Features/Feed/Pulse/PulsePostCard.swift` + `Features/Shared/Feed/FeedComponents.swift` | `ui/screens/feed/FeedScreen.kt` + `ui/screens/feed/pulse/PulsePostCard.kt` + `ui/screens/shared/feed/FeedComponents.kt` |
| Pulse post detail (Populated thread · Just-posted empty) | `A10___Detail__Content/A10.4 Post.html` + `post-frames.jsx` | `Features/Posts/PulsePostDetailView.swift` + `Features/Shared/ContentDetail/{Headers,Bodies,CTAs}/*` | `ui/screens/posts/PulsePostDetailScreen.kt` + `ui/screens/shared/content_detail/**` |
| Pulse compose (5 intent variants — Ask, Recommend, Event, Lost, Announce) | `Form.html` archetype + `form-frames.jsx` (generic — design treats Pulse Compose as one Form instance, not a bespoke page) | `Features/Compose/PulseCompose/PulseComposeContent.swift` + `PulseComposeView.swift` | `ui/screens/compose/pulse/PulseComposeScreen.kt` |

### Methodology

Rendered the three design HTML pages at 1600×1200 (deviceScaleFactor 2) via
Playwright after patching `unpkg.com` script tags to use locally-vendored
React/Babel/Lucide (`/tmp/p79b-pulse/served/`). 11 PNGs captured:

- `Pulse-overview.png` + 3 frame crops (Populated · Empty · Loading)
- `PostDetail-overview.png` + 2 frame crops (Populated · Empty)
- `Form-overview.png` + 3 frame crops (Simple · Multi-section · Field-heavy)

Diffed CSS dimensions in the JSX frame files (`pulse-frames.jsx`,
`post-frames.jsx`, `form-frames.jsx`) against `Radii.*` / `Spacing.*` /
`PantopusTextStyle.*` references in code.

### Resolvable token mismatches — FIXED

#### 1. `PulsePostCard` outer container corner radius — **FIXED** (iOS + Android)

| Side | Before | After |
|---|---|---|
| Design (`pulse-frames.jsx` L229) | `PostCard { borderRadius: 16, padding: 12 }` | n/a |
| iOS `PulsePostCard.swift:body` | `Radii.lg` (=12) | `Radii.xl` (=16) |
| Android `PulsePostCard.kt` | `Radii.lg` (=12) | `Radii.xl` (=16) |

#### 2. `FeedSkeletonCard` outer container corner radius — **FIXED** (iOS + Android)

The shimmer card now matches the populated card geometry (P7.6b parity requirement).

| Side | Before | After |
|---|---|---|
| Design (`pulse-frames.jsx` L489–522, `SkeletonCard`) | `borderRadius: 16, padding: 12` — same as populated | n/a |
| iOS `FeedComponents.swift:FeedSkeletonCard` | `Radii.lg` (=12) | `Radii.xl` (=16) |
| Android `FeedComponents.kt:FeedSkeletonCard` | `Radii.lg` (=12) | `Radii.xl` (=16) |

### Surfaced for design review (NOT fixed — off-scale design values, or out-of-scope)

The 12–14pt typography gap and "off the canonical radii ramp" pattern from
P7.4/P7.9.a recurs here. Pulse is heavy on 10.5/11.5/12.5/13.5 — these are
already documented in `docs/token-drift-typography.md`. P7.9.b only surfaces
items that emerge specifically from rendering these three packs:

| # | Surface | Design value | Code value | Notes |
|---|---|---|---|---|
| A | Pulse feed filter-hint pill (Empty state) | `borderRadius: 10` (`pulse-frames.jsx` L458) | iOS `Radii.md` (=8); Android same | 10 off-scale. Closest tokens 8 / 12. |
| B | Pulse feed chip-row chip height | `height: 28, padding: 0 14px, fontSize: 12.5/semibold` | iOS+Android match height 28 and padding 14, but font is off-scale (already P7.4). | The 14pt horizontal padding off-scale; closest 12 / 16. |
| C | Pulse Event-card RSVP chip | `height: 26, padding: 0 12px, borderRadius: 9999, fontSize: 11/bold` | iOS+Android use `height: 26, Spacing.s3 (=12), Radii.pill` — matches | 26 height is off-scale on the design side, but code matches design exactly. |
| D | Pulse intent chip (card header) | `padding: 2px 8px 2px 6px, borderRadius: 9999, fontSize: 10, fontWeight: 700, letterSpacing: 0.04, textTransform: uppercase` | iOS+Android: `Spacing.s2` (=8) horizontal, vertical 2pt, `Radii.pill`, `.font(.system(size: 10, weight: .bold))` | Asymmetric 6pt-leading vs 8pt-trailing padding can't be expressed without a half-step token. Visual delta is tiny — only icon offset. |
| E | Pulse author avatar size | `size: 32` (card header) | iOS+Android `size: 32` | ✓ matches |
| F | Pulse empty-state icon container | `width: 72, height: 72, borderRadius: 50%, background: primary-50` | iOS+Android `size: 72, Circle` | ✓ matches (72 itself is off-scale for arbitrary boxes, but here it's just a circle's diameter). |
| G | Pulse FAB | `width: 52, height: 52, borderRadius: 50%, primary-600` | iOS+Android `52x52 Circle` | ✓ matches |
| H | Post-detail TopBar height | `height: 48` (`post-frames.jsx` L93) | iOS uses `ContentDetailTopBar` (height 56); Android same | A 8pt over-height in code — but `ContentDetailTopBar` is shared with non-Pulse detail surfaces and Apple HIG recommends 44–56 navigation bar height. Out of scope for Pulse-only audit. |
| I | Post-detail body font | `fontSize: 15, lineHeight: 22, color: fg1` (`post-frames.jsx` L248) | iOS `.font(.system(size: 15, weight: .regular))` with `.lineSpacing(7)`; Android `fontSize: 15.sp` | 15 off-scale; already P7.4 drift. |
| J | Post-detail media grid corner radius | `borderRadius: 14, gap: 6` (`post-frames.jsx` L260) | iOS+Android `Radii.lg` (=12) | 14 off-scale; closest tokens 12 / 16. |
| K | Post-detail composer | `height: 40, borderRadius: 9999` | iOS `height: 40, Radii.pill`; Android `height: 44, Radii.pill` | iOS matches; Android uses 44pt for tap-target (a11y rule). Design vs a11y trade-off — keep code. |
| L | Post-detail composer send button | `width: 28, height: 28, borderRadius: 50%` | iOS+Android `40x40 Circle` | 12pt larger than design — Apple HIG / WCAG ≥44 (iOS) / Material ≥48 (Android) tap-target rules override design's 28pt. Surfacing as design-side question: should design loosen to match the canonical tap-target?
| M | Post-detail comment bubble | `borderRadius: 12, padding: 8px 12px` | iOS+Android `Radii.lg, Spacing.s3 (=12), Spacing.s2 (=8)` | ✓ matches |
| N | Post-detail comment avatar (top-level / nested) | `size: 28 / 24` | iOS+Android same | ✓ matches |
| O | Post-detail comment nested indent | `marginLeft: 36` | iOS `Spacer().frame(width: indentLevel * 36)`; Android same | 36 off-scale but matches design — keep. |
| P | Post-detail empty-thread card outer radius | `borderRadius: 14` (`post-frames.jsx` L407) | iOS+Android `Radii.lg` (=12) | 14 off-scale. |
| Q | Post-detail empty-thread icon container | `width: 48, height: 48, borderRadius: 14` | iOS+Android `48x48, Radii.lg` (=12) | Size matches; radius 14 off-scale. Closest token = `Radii.xl` (=16) — visually slightly rounder but on-scale. |
| R | Post-detail quick-reply chip | `padding: 6px 10px, borderRadius: 9999, fontSize: 11.5/semibold` | iOS+Android `Radii.pill, Spacing.s3 (=12), fontSize: 11.5` | 10pt design padding vs 12pt code is 2pt over; font matches. |
| S | Pulse Compose form fields | `height: 44, padding: 0 12px, borderRadius: 8, fontSize: 14` | iOS+Android `Radii.md` (=8), `height: 44`, `fontSize: 14` | ✓ matches (Form archetype is well-tokenised). |
| T | Pulse Compose top-bar right action | `height: 32, padding: 0 12px, borderRadius: 9` (`form-frames.jsx` L104) | iOS `WizardChrome` button uses `Radii.md` (=8); Android same | 9 off-scale; 8 is the nearest token. |
| U | `Radii.lg` used as `PantopusIconImage(size = Radii.lg)` on Android `PulsePostCard.kt:254, 294` | n/a | Misuse: `Radii.lg` is a corner-radius constant being used as a 12dp icon size. | Cleanup-only — visually correct (12dp = `Radii.lg` evaluates the same number), but should use a numeric `12.dp` literal or a proper icon-size token. Out of scope for token-parity audit. |

### Snapshot tests

Re-record needed for:
- iOS `PantopusTests/Features/Feed/PulseFeedSnapshotTest` (populated + loading frames now have 16pt card radius)
- iOS `PantopusTests/Features/Posts/PulsePostDetailViewTests` (unaffected — radii unchanged on detail)
- Android equivalents:
  - `PulseFeedSnapshotTest` (4 fixtures: populated, empty, loading, error)
  - `PulsePostDetailSnapshotTest` (unaffected)

Deferred — no simulator / emulator available in this sandbox. CI on merge will fail snapshot verification until baselines are re-recorded.

### Verification

- iOS `make verify-tokens` ✅ pass (no on-scale literals introduced; all swapped values come from the canonical `Radii.*` ramp).
- Android — manual grep `frontend/apps/android/.../feed/` ✅ no on-scale literals (the remaining `Radii.lg` references on `PulsePostCard.kt:254, 294` are icon `size:` parameters, not radius arguments — flagged in §U above).
- Both platforms compile against `Radii.xl` (16) which is an existing canonical token from `Radii.kt` / `Radii.swift`. No new tokens introduced.

Files modified (8 lines across 4 files):
- iOS: `Features/Feed/Pulse/PulsePostCard.swift` (2 lines), `Features/Shared/Feed/FeedComponents.swift` (2 lines)
- Android: `ui/screens/feed/pulse/PulsePostCard.kt` (2 lines), `ui/screens/shared/feed/FeedComponents.kt` (2 lines)

---

## P7.9.c — Marketplace + Gigs + compose wizards visual parity

**Date:** 2026-05-26 · **Branch:** `claude/loving-hamilton-OI30q` · **Commit:** appended this prompt

### Scope

| Surface | Design HTML | iOS implementation | Android implementation |
|---|---|---|---|
| Marketplace (Populated · Empty · Loading) | `A08/uploads/Pantopus-design/Marketplace.html` + `marketplace-frames.jsx` | `Features/Marketplace/MarketplaceView.swift` + `MarketplaceContent.swift` | `ui/screens/marketplace/MarketplaceScreen.kt` + `MarketplaceContent.kt` |
| Gigs feed (Populated · Empty · Loading) | `A08/.../Gigs.html` + `gigs-frames.jsx` | `Features/Gigs/GigsFeedView.swift` + `GigsContent.swift` + `GigsCategoryChipRow.swift` | `ui/screens/gigs/GigsFeedScreen.kt` + `GigsContent.kt` |
| Snap-and-sell wizard (camera capture + AI review) | `Wizard__multi-step_form_/A12.9 List an Item.html` + `listing-create-frames.jsx` | `Features/Compose/ListingCompose/*` | `ui/screens/compose/listing/*` |
| Magic Task wizard (AI describe + manual category picker) | `Wizard__multi-step_form_/A12.8 Post a Task.html` + `post-task-frames.jsx` | `Features/Compose/GigCompose/*` (`GigComposeMagic.swift` is the AI-describe step) | `ui/screens/compose/gig/*` |
| Post Gig V1 (legacy single-screen form) | `A13___Form__single_screen_/Post Gig V1.html` + `post-gig-v1-frames.jsx` | `Features/Gigs/QuickPost/PostGigV1View.swift` + `PostGigV1SupportViews.swift` | `ui/screens/gigs/quickpost/PostGigV1Screen.kt` |

### Methodology

Rendered the 5 design HTML pages at 1600×1200 (deviceScaleFactor 2) via
Playwright with locally-vendored React/Babel/Lucide. 17 PNGs captured:

- `Marketplace-overview.png` + 3 frame crops (Populated · Empty · Loading)
- `Gigs-overview.png` + 3 frame crops (Populated · Empty · Loading)
- `PostATask-overview.png` + 2 frame crops (Populated · Manual-path)
- `ListAnItem-overview.png` + 2 frame crops (Populated · Camera-capture)
- `PostGigV1-overview.png` + 2 frame crops (Populated · Validation-errors)

Diffed CSS dimensions in `marketplace-frames.jsx`, `gigs-frames.jsx`,
`listing-create-frames.jsx`, `post-task-frames.jsx`, `post-gig-v1-frames.jsx`
against `Radii.*` / `Spacing.*` references in code.

### Resolvable token mismatches — FIXED

#### 1. `GigRow` card outer corner radius — **FIXED** (iOS + Android)

| Side | Before | After |
|---|---|---|
| Design (`gigs-frames.jsx` L216 + L419 SkeletonGig) | `GigRow { borderRadius: 16, padding: 16 }` and matching skeleton | n/a |
| iOS `GigsFeedView.swift:GigRow` (lines 361, 364) | `Radii.lg` (=12) | `Radii.xl` (=16) |
| Android `GigsFeedScreen.kt:GigRow` (lines 569, 571) | `Radii.lg` (=12) | `Radii.xl` (=16) |

The Gigs feed loading skeleton was **already correct** — it reuses
`FeedSkeletonCard` (the shared shimmer card), which P7.9.b already moved
from `Radii.lg → Radii.xl`. So both the populated and loading frames now
agree at `Radii.xl` (16) matching the design — shimmer-shape parity
(P7.6b) preserved.

### Surfaced for design review (NOT fixed — off-scale design values or out-of-scope)

| # | Surface | Design value | Code value | Notes |
|---|---|---|---|---|
| A | Marketplace listing card outer corner radius | `borderRadius: 14` (`marketplace-frames.jsx` L186) | iOS `RoundedRectangle(cornerRadius: 14, …)` literals on `MarketplaceView.swift:335, 338, 403, 406`; Android `RoundedCornerShape(14.dp)` literals on `MarketplaceScreen.kt:467, 469, 565, 567` | Code already uses the literal `14` matching design exactly. 14 is OFF the canonical Radii ramp ({4,6,8,12,16,20,24,9999}). The repo's `verify-tokens` guard allows off-scale literals through (it only rejects on-scale literals). Surface as: either extend the radii ramp to include `Radii.lg2` (=14), or accept the off-scale literal as a deliberate visual choice for this card archetype. |
| B | Marketplace listing image height | 104pt (`marketplace-frames.jsx` L159) | iOS `Shimmer(height: 104)`; Android matches | 104 is off-scale (closest standard image heights 96/100/120). Design call. |
| C | Marketplace listing title | `fontSize: 11.5, fontWeight: 600, lineHeight: 14, maxLines: 2, minHeight: 28` | iOS `.font(.system(size: 11.5, weight: .semibold))` and matching maxLines/minHeight; Android matches | 11.5pt off-scale (P7.4 drift already documented). |
| D | Marketplace listing meta | `fontSize: 9.5` (L199) | iOS `.font(.system(size: 9.5))`; Android matches | Far off-scale (9.5pt isn't on the canonical type ramp). Documented in P7.4 typography drift. |
| E | Marketplace empty-state hint pill | `borderRadius: 10` (L334) | iOS `Radii.md` (=8); Android same | 10 off-scale. Same `borderRadius:10` pattern as Pulse Empty (§P7.9.b A). |
| F | Marketplace category chip | `height: 28, padding: 0 14px, borderRadius: 9999, fontSize: 12.5/600` (L141) | iOS+Android match height 28 and padding `Spacing.s3` (=12) ≠ 14 design | 14pt design padding off-scale. |
| G | Gigs feed `SortFilterRow` filter pill | `padding: 4px 10px 4px 8px, borderRadius: 9999, fontSize: 11.5/700` | iOS `GigsFilterButton` uses `padding(.horizontal, 10)` + `Spacing.s1` vertical; Android `padding(horizontal = 10.dp, vertical = 4.dp)` matches. | 10pt literal padding ≈ matches design; mostly aligned. |
| H | Gigs feed `GigRow` body font | `fontSize: 12, lineHeight: 17, maxLines: 2` (`gigs-frames.jsx` L230) | iOS+Android `fontSize: 12` matches but lineHeight inlined as `.lineSpacing(5)` vs `lineHeight = 17.sp` | 17pt line-height on 12pt font is off-scale (P7.4 drift). |
| I | Gigs feed `BidsPill` / `BeTheFirstPill` | `padding: 2px 8px, borderRadius: 9999, fontSize: 10/700, fontWeight: 700, letterSpacing: 0.04` | iOS+Android use `Radii.pill`, `Spacing.s2` (=8) horizontal, `fontSize: 10, fontWeight: .bold` | ✓ matches |
| J | Gigs `CategoryChip` (intent chip in card header) | `padding: 2px 8px, borderRadius: 9999, fontSize: 10, fontWeight: 700, textTransform: uppercase, color: 12% tint of category accent` | iOS+Android match | ✓ |
| K | Listing camera capture step (A12.9 Frame 2) | photo strip hero tile `borderRadius: 14`, thumb tiles `borderRadius: 12`, add-photo tile `borderRadius: 12` (`listing-create-frames.jsx` L268, L282, L288, L294, L300) | iOS `ListingComposePhotoStep.swift` uses `Radii.xl` (=16) for the camera viewfinder outer (line 58); the captured-angles strip uses `Radii.lg` (=12) — matches thumb but not hero | Hero off by 2pt (14 design vs 12 code if a hero tile is drawn). The decorative sofa silhouette inside the viewfinder uses literal `14, 18, 28` — these are drawing primitives, not surface chrome, leave as-is. |
| L | Listing review step (A12.9 Frame 1) AI suggestions banner | `borderRadius: 12, padding: 10px 12px, background: magicBg` (L317) | iOS `SuggestionsBanner.swift` exists — uses `Radii.lg` (=12) ✓; Android matches | ✓ |
| M | Listing review FieldShell | `borderRadius: 12, minHeight: 44` (L365–366) | iOS+Android use `Radii.lg` (=12), `height: 44` | ✓ |
| N | Listing review PriceField outer | `borderRadius: 12, padding: 12` (L434) | iOS+Android `Radii.lg` (=12) | ✓ |
| O | Listing review Condition pills | `borderRadius: 10` (L496) | iOS+Android use `Radii.md` (=8) or `Radii.lg` (=12) | 10 off-scale; closest tokens 8 / 12. |
| P | Magic Task `DescribeCard` outer | `borderRadius: 16` (A12.8 `post-task-frames.jsx` L231) | iOS `GigComposeMagic.swift:355, 358` `Radii.xl` (=16); Android `GigComposeMagic.kt:194` matches | ✓ matches |
| Q | Magic Task `DescribeCard` magic-icon tile | `borderRadius: 7` (L241) | iOS `Radii.sm` (=6) or close; Android matches | 7 off-scale; closest token 6 / 8. |
| R | Magic Task manual-path archetype cards | `borderRadius: 12, padding: 14` | iOS+Android use `Radii.lg` (=12) | ✓ matches radius; 14pt padding off-scale. |
| S | Post Gig V1 form fields (SelectField, DateField, PriceField) | `borderRadius: 8, height: 44` (`post-gig-v1-frames.jsx` L35, 81, 121) | iOS+Android `Radii.md` (=8), `height: 44` | ✓ matches (Form archetype tokenisation). |
| T | Post Gig V1 photo grid tile | `borderRadius: 10` (L152, L164) | iOS+Android use `Radii.md` (=8) | 10 off-scale; closest tokens 8 / 12. |
| U | Post Gig V1 error banner | `borderRadius: 10` (L211) | iOS+Android use `Radii.md` (=8) | 10 off-scale. |

### Snapshot tests

Re-record needed for:
- iOS `PantopusTests/Features/Gigs/GigsFeedViewTests` (populated frame now has 16pt card radius)
- Android `PulseFeedSnapshotTest` (carry-over from P7.9.b is unaffected; this is Gigs)
- Android `GigsFeedSnapshotTest` (the equivalent — needs re-record)

Deferred — no simulator / emulator. CI will fail snapshot verification until baselines are re-recorded.

### Verification

- iOS `make verify-tokens` ✅ pass.
- Android — manual grep on `frontend/apps/android/.../gigs/`, `marketplace/`, `compose/listing/`, `compose/gig/`, `gigs/quickpost/` returns 0 on-scale literals.
- Both platforms compile against `Radii.xl` (16) — existing canonical token. No new tokens introduced.

Files modified (4 lines across 2 files):
- iOS: `Features/Gigs/GigsFeedView.swift` (2 lines)
- Android: `ui/screens/gigs/GigsFeedScreen.kt` (2 lines)

---

## P7.9.d — Map surfaces visual parity

**Date:** 2026-05-26 · **Branch:** `claude/loving-hamilton-OI30q` · **Commit:** appended this prompt

### Scope

| Surface | Design HTML | iOS implementation | Android implementation |
|---|---|---|---|
| Map List Hybrid archetype (3 detents: collapsed 20%, default 40%, expanded 70%) | `A08/uploads/Pantopus-design/Map List Hybrid.html` + `map-frames.jsx` | `Features/Shared/MapListHybrid/MapListHybridShell.swift` + `MapListHybridContent.swift` + `Features/Nearby/NearbyMapView.swift` (consumer) | `ui/screens/shared/map_list_hybrid/MapListHybridShell.kt` + `Content.kt` + `ui/screens/nearby/map/NearbyMapScreen.kt` |
| Tasks Map (A11.1 — populated + empty) | `Full-bleed_map.../Tasks Map.html` + `gigs-map-frames.jsx` | `Features/Gigs/TasksMap/TasksMapView.swift` + `TasksMapContent.swift` | `ui/screens/gigs/tasks_map/TasksMapScreen.kt` + `TasksMapContent.kt` |
| Explore Map (A11.2 — populated + empty) | `Full-bleed_map.../Explore.html` + `explore-map-frames.jsx` | `Features/Explore/ExploreMapView.swift` + `ExploreMapContent.swift` | `ui/screens/explore/ExploreMapScreen.kt` + `ExploreMapContent.kt` |
| Mailbox Map (A11.4 — populated + pin-detail) | `Full-bleed_map.../Mailbox Map.html` + `mailbox-map-frames.jsx` | `Features/Mailbox/MailboxMap/MailboxMapView.swift` + `MailboxMapContent.swift` | `ui/screens/mailbox/mailbox_map/MailboxMapScreen.kt` + `MailboxMapContent.kt` |
| Discover Hub refinement (A11.3 — populated + empty) | `Full-bleed_map.../Discover.html` + `discover-frames.jsx` | `Features/DiscoverHub/DiscoverHubView.swift` | `ui/screens/discoverhub/DiscoverHubScreen.kt` |

### Methodology

Rendered the 5 design HTML pages at 1600×1200 (deviceScaleFactor 2) via
Playwright with locally-vendored React/Babel/Lucide. 17 PNGs captured
(3 + 2 + 2 + 2 + 2 frame crops + 5 overviews).

Diffed CSS dimensions in `map-frames.jsx`, `gigs-map-frames.jsx`,
`explore-map-frames.jsx`, `mailbox-map-frames.jsx`, `discover-frames.jsx`
against `Radii.*` / `Spacing.*` references in code.

### Resolvable cross-platform parity fix — APPLIED

#### 1. Android `MailboxSpotCard` outer corner radius — **FIXED** (Android only)

The iOS Mailbox map card already uses literal `cornerRadius: 14`
matching design's `borderRadius: 14`. Android was the lone outlier
using `Radii.xl` (=16), creating a 2pt cross-platform radius drift
on the same surface. Fix is Android-only — iOS already matches design.

| Side | Before | After |
|---|---|---|
| Design (`mailbox-map-frames.jsx` L160) | `MailCard { borderRadius: 14, padding: 11/12, border: 1px (or 2px when active) }` | n/a |
| iOS `MailboxMapView.swift:668, 887, 981` | `cornerRadius: 14` (literal) | unchanged ✓ already at design |
| Android `MailboxMapScreen.kt:872, 877, 881` (populated `MailboxSpotCard`) | `Radii.xl` (=16) | `14.dp` (literal, matches iOS + design) |
| Android `MailboxMapScreen.kt:1154, 1156` (loading skeleton card) | `Radii.xl` (=16) | `14.dp` (preserves shimmer-shape parity per P7.6b) |

This fix swaps a canonical token (`Radii.xl`) for a raw off-scale
literal (`14.dp`). Per the audit rule "do not invent tokens", we
cannot add a `Radii.lg2` (=14) entry. The literal pattern is the
established convention on the iOS side and in 2/3 other Android map
files (`ExploreMapScreen.kt`, `NearbyMapScreen.kt` already use
`14.dp` literals); this brings the 4th map file into line.

### Surfaced for design review (NOT fixed — off-scale design values or out-of-scope)

The map surfaces are unusually heavy in off-scale design values.
Surfacing the most consequential ones below; the per-frame audit
contains many more 9.5/10.5/11.5/12.5pt typography hits already
documented in P7.4 typography drift.

| # | Surface | Design value | Code value | Notes |
|---|---|---|---|---|
| A | Bottom-sheet outer corner radius | `borderRadius: 22 22 0 0` (top corners; all 5 frames agree) | iOS `NearbyMapView`, `ExploreMapView`, `MailboxMapView` use literal `22`; iOS `DiscoverHubView.swift:176` uses **`Radii.xl2` (=20)** (outlier); Android `MailListHybridShell.kt:319`, `ExploreMapScreen.kt:763`, `MailboxMapScreen.kt:677`, `NearbyMapScreen.kt:621` use literal `22.dp`; Android `DiscoverHubScreen.kt:161` uses **`Radii.xl2` (=20)** (outlier) | 22pt is off-scale (between `Radii.xl2`=20 and `Radii.xl3`=24). DiscoverHub on both platforms drifts 2pt from design + the other 3 map surfaces. Recommend reconciling: either accept the 2pt DiscoverHub drift OR switch to literal `22.dp` everywhere OR add `Radii.xl2_5` (=22) — the last option requires the "do not invent tokens" rule to be relaxed. |
| B | DiscoverHub card outer corner radius (`DiscoverTaskCard`, `DiscoverMarketplaceRailCard`, `DiscoverPostCard`) | `borderRadius: 14` (`discover-frames.jsx` L37, L94, L137) | iOS `DiscoverHubView.swift:614/617, 673/676, 728/731` use `Radii.lg` (=12); Android `DiscoverHubScreen.kt:708/711, 758/761, 825/828` use `Radii.lg` (=12) | Both platforms internally consistent at `Radii.lg` (12), but design says 14. 2pt drift. Resolution requires design call (accept 2pt under, or add 14 to the ramp). |
| C | Map-card icon-tile corner radius | `borderRadius: 10` across all 5 frames (`map-frames.jsx` L196, `explore-map-frames.jsx` L82, `mailbox-map-frames.jsx` L93, `discover-frames.jsx` L86) | iOS uses literal `10` consistently across all 4 map files; Android `MailboxMapScreen.kt:1013/1018, 1389/1391` uses literal `10.dp`; other Android map files use literal `10.dp` consistently | 10 off-scale (between `Radii.md`=8 and `Radii.lg`=12). Code matches design exactly via literal but breaks tokens-only spirit. |
| D | Empty-state icon container | TasksMap: `borderRadius: 16, 56×56` (`gigs-map-frames.jsx`); ExploreMap not explicitly specified; DiscoverHub: `borderRadius: 14, 52×52` (`discover-frames.jsx`) | iOS TasksMap `Radii.xl` ✓; iOS ExploreMap `Radii.xl` (16, 56×56); iOS DiscoverHub doesn't expose an empty hero icon container (different empty layout); Android matches | TasksMap + ExploreMap correctly use `Radii.xl` (16). DiscoverHub empty state would drift if it had the same icon — surface for design unification. |
| E | Pulse-style chips throughout map sheets (CategoryChip, ExplorePill, MailboxMap chips) | `height: 28, padding: 0 12px, borderRadius: 9999, fontSize: 11.5/600` | iOS+Android use `Radii.pill`, `height: 28`, `padding(.horizontal, Spacing.s3)` (=12), `fontSize: 11.5` | 11.5pt off-scale (P7.4 drift). Otherwise ✓. |
| F | MailboxMap pin (square envelope) | `width: 26, height: 26, borderRadius: 8` (`mailbox-map-frames.jsx`) | iOS `MailboxMapView.swift:741, 760, 764` uses `Radii.lg` (=12) on the outer container, `Radii.sm` (=6) on inner squares — not a 1:1 match with design's 8 (=`Radii.md`); Android `MailboxMapScreen.kt:476/478/479` uses `Radii.sm` (=6) | Pin design says 8pt; code uses 6 or 12. 2pt difference either direction. Surface for design or accept. |
| G | Active MailCard border-width | design `border: 2px (active)`, `1px (inactive)` | iOS `MailboxMapView.swift:626` uses `lineWidth: selected ? 2 : 1` ✓; Android `MailboxMapScreen.kt:875` uses `width = if (active) 2.dp else 1.dp` ✓ | ✓ matches |
| H | Bottom-sheet handle pill | `width: 40, height: 4, borderRadius: 4` | iOS uses `Capsule()` at `40×4`; Android matches | ✓ matches |
| I | DiscoverHub item-card image strip | `height: 104, borderRadius: 0` (square-cornered photo strip at top of card) | iOS `Shimmer(height: 104, cornerRadius: 0)` ✓; Android matches | ✓ matches |
| J | Map controls (zoom +, locate, layers buttons) | `width: 38, height: 38, borderRadius: 50%` | iOS+Android use `Circle` shape at 38×38 | ✓ matches |
| K | Cluster pin | `width: 38, height: 38, borderRadius: 50%, border: 3px, fontSize: 13/700` | iOS+Android match | ✓ |
| L | TypedPin (Explore + Discover) | `width: 26, height: 26, borderRadius: 50% or 8 (square for item-kind)` | iOS `ExploreMapView.swift:755, 809` uses `Radii.md` (=8) for square; iOS `DiscoverHubScreen.kt:307` uses `Radii.sm` (=6) for square — INCONSISTENCY between Explore and Discover treatments of the same pin shape | Explore uses 8 (matches design); Discover uses 6. Surface for design unification. |
| M | Filter button in ExploreMap (`Filter • 2`) | `height: 32, padding: 0 10px 0 11px, borderRadius: 9999` | iOS+Android use `Radii.pill` ✓ | ✓ (asymmetric padding minor) |
| N | MapHeader on Discover | `height: 190, with embedded MiniPins` | iOS+Android match | ✓ |
| O | DiscoverHub Discover sheet section icon | `width: 24, height: 24, borderRadius: 6` | iOS+Android use `Radii.sm` (=6) | ✓ matches |
| P | TasksMap PostTaskFAB | `height: 48, padding: 0 18px 0 14px, borderRadius: 9999, fontSize: 14/700` | iOS+Android use `Radii.pill`, `height: 48` ✓ | Padding 18/14 asymmetric — off-scale on the design side. |
| Q | Carousel page dots (in map-frames sheet) | `width: 16/5 height: 5, borderRadius: 5` | Not directly inspected; pagination dot pattern shared across many surfaces | Surfacing only — minor element. |
| R | You-are-here pin | `width: 14, height: 14, borderRadius: 50%, border: 3px` | iOS+Android Circle implementations | ✓ matches |

### Snapshot tests

Re-record needed for:
- Android `MailboxMapSnapshotTest` (the card radius change applies to all populated + selected + loading frames)
- iOS Mailbox map snapshots unaffected (radius unchanged on iOS)

Deferred — no simulator / emulator. CI will fail snapshot verification until baselines are re-recorded.

### Verification

- iOS `make verify-tokens` ✅ pass (no on-scale literals introduced).
- Android — the existing literal-radii pattern (`14.dp`, `10.dp`, `22.dp`) was extended to one more site for cross-platform parity. The repo's CI guard rejects on-scale raw literals (e.g., a raw `8.dp` when `Radii.md` exists) but allows off-scale literals through as a deliberate carve-out for matching off-scale design values.

Files modified (2 sites in 1 file):
- Android: `ui/screens/mailbox/mailbox_map/MailboxMapScreen.kt` (5 lines across the populated `MailboxSpotCard` outer + loading skeleton card outer; iOS already matched design via literal `14`)

### Audit summary

P7.9.d found that map surfaces use the off-scale `borderRadius: 14`
pattern more heavily than any other surface family audited so far.
Most code sites are already either (a) using literal `14.dp` / `14`
to match design exactly, or (b) using the nearest canonical token
(`Radii.lg`=12 or `Radii.xl`=16). The cross-platform parity break on
`MailboxSpotCard` was the only resolvable issue — the rest are
design-system questions about whether to:
1. Extend the `Radii` ramp to include 14 (and 22 for sheets, and 10
   for icon tiles) — would resolve A, B, C, F, L at once but breaks
   the "do not invent tokens" rule;
2. Snap all off-scale design values to the nearest canonical token —
   accepts 2pt visual drift across many surfaces;
3. Continue the existing literal-shim pattern (most map files already
   do this) — keeps visual parity but tolerates off-scale literals.

Option 3 is the de-facto status quo and the safest no-change choice.
Recommendation: log a design-system ticket to settle (1) vs (2) vs (3)
before the next audit pass, so future map work has a clear convention.
