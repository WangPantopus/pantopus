#!/usr/bin/env bash
# Compile gate for the RN→native parity waves.
#   ./gate.sh ios      → xcodegen + xcodebuild (simulator, Debug)
#   ./gate.sh android  → :app:compileDebugKotlin on temurin-17
#   ./gate.sh both     → ios then android
# Writes full logs to the scratchpad and prints only the error lines.
set -uo pipefail

ROOT=/Users/yingpengwang/pantopus/native/pantopus
LOGDIR="${GATE_LOGDIR:-/private/tmp/claude-501/-Users-yingpengwang-pantopus-native-pantopus/d0f4b99e-f373-4e48-958e-054ab1d6949b/scratchpad}"
mkdir -p "$LOGDIR"

gate_ios() {
  echo "=== iOS gate ==="
  ( cd "$ROOT/frontend/apps/ios" && make build ) >"$LOGDIR/gate-ios.log" 2>&1
  local rc=$?
  if [ $rc -eq 0 ] && grep -q "BUILD SUCCEEDED" "$LOGDIR/gate-ios.log"; then
    echo "iOS: BUILD SUCCEEDED"
  else
    echo "iOS: FAILED (rc=$rc) — log $LOGDIR/gate-ios.log"
    grep -E "error:|error :|Undefined symbol|fatal error" "$LOGDIR/gate-ios.log" \
      | sed "s|$ROOT/frontend/apps/ios/||" | sort -u | head -80
  fi
  return $rc
}

gate_android() {
  echo "=== Android gate ==="
  ( cd "$ROOT/frontend/apps/android" \
    && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
       ./gradlew :app:compileDebugKotlin --console=plain ) >"$LOGDIR/gate-android.log" 2>&1
  local rc=$?
  if [ $rc -eq 0 ]; then
    echo "Android: BUILD SUCCESSFUL"
  else
    echo "Android: FAILED (rc=$rc) — log $LOGDIR/gate-android.log"
    grep -E "^e: |error: |Caused by|Unresolved reference" "$LOGDIR/gate-android.log" \
      | sed "s|file://$ROOT/frontend/apps/android/||" | sort -u | head -80
  fi
  return $rc
}

case "${1:-both}" in
  ios) gate_ios ;;
  android) gate_android ;;
  both) gate_ios; i=$?; gate_android; a=$?; exit $(( i || a )) ;;
  *) echo "usage: gate.sh [ios|android|both]"; exit 2 ;;
esac
