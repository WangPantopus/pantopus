# Cross-platform inventory diff (P6.7)

iOS (SwiftUI) ↔ Android (Jetpack Compose) per-screen parity audit. Every
screen built across Tiers 0–6 is compared on seven dimensions; any deltas
are listed under that screen. A dimension marked **Match** was verified
equivalent on both platforms after reading both sides. **N/A** marks a
dimension that does not apply (e.g. a screen with no inputs has no field
set). A screen with all-seven **Match** has parity.

This report only *records* drift. It does **not** fix anything — fixes are
the next prompt (P6.8).

## Dimensions compared

1. **State coverage** — loading / empty / populated / error (all four
   required for fetchable screens; forms & static pages legitimately have
   fewer — noted where so).
2. **Action affordances** — every button / link / tap target on one
   platform exists on the other.
3. **Field set** — every input field on one platform exists on the other.
4. **Validation rules** — same constraints (required, length, regex, …).
5. **Empty-state copy** — must match word-for-word.
6. **Error-state copy** — must match word-for-word.
7. **Animation / transition** — push vs sheet vs modal must match.

Paths in each section are relative to:
- iOS — `frontend/apps/ios/Pantopus/Features/`
- Android — `frontend/apps/android/app/src/main/java/app/pantopus/android/ui/screens/`

## Summary

<!-- SUMMARY -->
_(Filled in once all sections are complete.)_

---

<!-- APPEND-HERE -->
