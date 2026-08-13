#!/usr/bin/env bash
# Reachability audit for the parity branch.
#
# The original audit's dominant failure mode was screens that existed and
# compiled but had no production entry point (CeremonialMail wizard behind
# #if DEBUG, DisambiguateMailForm behind a debug dialog, listGuestPasses /
# revokeGuestPass / earnBalance / pending declared with zero call sites).
# This re-checks that shape for everything the branch added.
set -uo pipefail
ROOT=/Users/yingpengwang/pantopus/native/pantopus
AND="$ROOT/frontend/apps/android/app/src/main/java/app/pantopus/android"
IOS="$ROOT/frontend/apps/ios/Pantopus"
cd "$ROOT"

echo "############ ANDROID ROUTES ############"
printf '%-32s %-10s %-10s %s\n' ROUTE COMPOSABLE NAV_SITES VERDICT
git diff master...HEAD -- "${AND#$ROOT/}/ui/screens/root/RootTabScreen.kt" \
  | grep -E '^\+[[:space:]]+const val [A-Z_]+' \
  | sed -E 's/^\+[[:space:]]+const val ([A-Z_]+).*/\1/' \
  | grep -vE '_KEY$' | sort -u \
  | while read -r r; do
      [ -z "$r" ] && continue
      # a composable(...) block registered for it. Registration is usually
      # multi-line (`composable(\n    route = ChildRoutes.X,`), so match the
      # `route =` form as well as the single-line positional form.
      comp=$(grep -cE "route = ChildRoutes\.$r,|composable\(ChildRoutes\.$r[,)]" \
             "$AND/ui/screens/root/RootTabScreen.kt")
      # Parameterised routes are navigated through a camelCase builder
      # (GUEST_PASSES -> ChildRoutes.guestPasses(homeId)), not the constant,
      # so count both forms.
      camel=$(echo "$r" | awk -F_ '{printf "%s", tolower($1); for(i=2;i<=NF;i++) printf "%s%s", toupper(substr($i,1,1)), tolower(substr($i,2))}')
      nav=$(grep -rnE "ChildRoutes\.($r\b|$camel\()" "$AND" --include='*.kt' 2>/dev/null \
            | grep -vE "const val $r|route = ChildRoutes\.$r,|fun $camel\(" | wc -l | tr -d ' ')
      if [ "$comp" -gt 0 ] && [ "$nav" -gt 0 ]; then v=ok
      elif [ "$comp" -eq 0 ]; then v="NO-COMPOSABLE"
      else v="NO-NAV-SITE"; fi
      printf '%-32s %-10s %-10s %s\n' "$r" "$comp" "$nav" "$v"
    done

echo
echo "############ ANDROID: new Api methods with zero repository/VM call sites ############"
git diff master...HEAD --name-only -- "${AND#$ROOT/}/data/api/services" \
  | while read -r f; do
      [ -f "$f" ] || continue
      iface=$(basename "$f" .kt)
      grep -oE 'suspend fun [a-zA-Z0-9_]+' "$f" | awk '{print $3}' | sort -u \
      | while read -r m; do
          [ -z "$m" ] && continue
          n=$(grep -rn "\.$m(" "$AND" --include='*.kt' 2>/dev/null | grep -vc "$f")
          [ "$n" -eq 0 ] && echo "  ZERO CALL SITES: $iface.$m"
        done
    done
echo "  (done)"

echo
echo "############ iOS: new endpoint helpers with zero call sites ############"
git diff master...HEAD --name-only -- "${IOS#$ROOT/}/Core/Networking/Endpoints" \
  | while read -r f; do
      [ -f "$f" ] || continue
      base=$(basename "$f" .swift)
      grep -oE 'static func [a-zA-Z0-9_]+' "$f" | awk '{print $3}' | sort -u \
      | while read -r m; do
          [ -z "$m" ] && continue
          n=$(grep -rn "\.$m(" "$IOS" 2>/dev/null | grep -vc "$f")
          [ "$n" -eq 0 ] && echo "  ZERO CALL SITES: $base.$m"
        done
    done
echo "  (done)"

echo
echo "############ BOTH: production entry points still behind debug gates ############"
grep -rn "BuildConfig.DEBUG" "$AND/ui/screens/you/YouScreen.kt" 2>/dev/null | head -20
echo "  --- iOS ---"
grep -rn "me\.debug\." "$IOS/Features/Me/MeViewModel.swift" 2>/dev/null | head -20
echo "  (done)"
