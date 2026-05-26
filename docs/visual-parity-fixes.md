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
