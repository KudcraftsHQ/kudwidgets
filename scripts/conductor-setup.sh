#!/usr/bin/env bash
# Conductor worktree provisioning for kud-widgets (an Android/Gradle app).
#
# Run by `conductor adopt` / `conductor worktree create` with the worktree as CWD.
# There is no web server and no database: a worktree only needs what the Gradle build reads that git
# doesn't carry, which is local.properties (sdk.dir). The Gradle wrapper jar is committed.
#
# Conductor gives us CONDUCTOR_WORKSPACE_NAME and CONDUCTOR_ROOT_PATH (the root checkout).
# Knobs: ANDROID_HOME (default ~/.local/share/android-sdk), JAVA_HOME (default ~/.local/share/jdk/current).
#
# NOTE: conductor reads conductor.json, and so this script, from the ROOT checkout, not from the worktree's
# branch. Changes here only take effect for new worktrees once they are on main.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/.local/share/android-sdk}"
JDK="${JAVA_HOME:-$HOME/.local/share/jdk/current}"

echo "=== kud-widgets conductor setup: ${CONDUCTOR_WORKSPACE_NAME:-unknown} ==="

echo "[1/2] local.properties..."
if [ -f local.properties ]; then
  echo "  already here, kept"
elif [ -n "${CONDUCTOR_ROOT_PATH:-}" ] && [ "$PWD" != "$CONDUCTOR_ROOT_PATH" ] && [ -f "$CONDUCTOR_ROOT_PATH/local.properties" ]; then
  cp "$CONDUCTOR_ROOT_PATH/local.properties" local.properties
  echo "  copied from the root checkout"
else
  echo "sdk.dir=$SDK" > local.properties
  echo "  wrote sdk.dir=$SDK"
fi

echo "[2/2] Toolchain..."
sdk_dir="$(sed -n 's/^sdk.dir=//p' local.properties)"
[ -d "$sdk_dir" ] && echo "  Android SDK: $sdk_dir" || echo "  WARNING: Android SDK not found at $sdk_dir" >&2
[ -x "$JDK/bin/java" ] && echo "  JDK: $JDK" || echo "  WARNING: no JDK at $JDK; set JAVA_HOME" >&2
chmod +x gradlew

echo ""
echo "Setup complete. The conductor window builds a debug APK (scripts/conductor-run.sh)."
