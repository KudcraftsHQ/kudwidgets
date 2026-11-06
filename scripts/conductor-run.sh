#!/usr/bin/env bash
# The "dev server" for kud-widgets: there is none. An Android app has nothing to serve, so this builds a
# debug APK once, as a check that the worktree compiles, and then idles.
#
# It has to stay up: when `conductor run` exits, conductor's tmux window drops to its restart prompt, and the
# T3 watcher presses Enter again on every tick while a thread is unsettled, rebuilding over and over.
# Rebuild by stopping this (Ctrl-C) and pressing Enter, or `conductor t3 dev restart`.
set -uo pipefail

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk/current}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/.local/share/android-sdk}"

echo "=== kud-widgets: assembleDebug ==="
# --no-daemon and an in-process Kotlin compiler, so nothing stays resident after the build (hammas-dev has 16 GB).
if ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process assembleDebug; then
  echo ""
  echo "Built: $(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | tr '\n' ' ')"
else
  echo ""
  echo "assembleDebug FAILED (see above)."
fi
echo "No server to run for an Android app; idling. Ctrl-C then Enter to rebuild."
exec tail -f /dev/null
