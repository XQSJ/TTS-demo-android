#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 /path/to/TTSTRAINER/artifacts/<model_name> [language ...]" >&2
  exit 2
fi

SOURCE="$(cd "$1" && pwd)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/app/src/main/assets/tts"

if [[ ! -f "$SOURCE/model.onnx.json" ]]; then
  echo "Invalid export: expected model.onnx.json" >&2
  exit 1
fi

mkdir -p "$TARGET"
find "$TARGET" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp "$SOURCE/model.onnx.json" "$TARGET/"
for optional in frontend.json frontend.conformance.json tokens.json; do
  [[ ! -f "$SOURCE/$optional" ]] || cp "$SOURCE/$optional" "$TARGET/"
done
if [[ -d "$SOURCE/android_text" ]]; then
  cp -R "$SOURCE/android_text" "$TARGET/"
  echo "Legacy eSpeak mobile model installed in: $TARGET"
  exit 0
fi

PACK_ROOT="$SOURCE/frontend-packs"
if [[ ! -f "$SOURCE/model.onnx" || ! -f "$PACK_ROOT/manifest.json" ]]; then
  echo "Invalid routed export: expected model.onnx and frontend-packs/manifest.json" >&2
  exit 1
fi

cp "$SOURCE/model.onnx" "$TARGET/"
mkdir -p "$TARGET/frontend-packs"
cp "$PACK_ROOT/manifest.json" "$TARGET/frontend-packs/"
[[ ! -d "$PACK_ROOT/_shared" ]] || cp -R "$PACK_ROOT/_shared" "$TARGET/frontend-packs/"
shift
if [[ $# -eq 0 ]]; then
  set -- $(find "$PACK_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name _shared -exec basename {} \; | sort)
fi
for language in "$@"; do
  if [[ ! -f "$PACK_ROOT/$language/manifest.json" ]]; then
    echo "Unknown or unavailable frontend pack: $language" >&2
    exit 1
  fi
  cp -R "$PACK_ROOT/$language" "$TARGET/frontend-packs/"
done

echo "Routed model core installed in: $TARGET"
echo "Frontend descriptors installed: $*"
echo "Provider runtimes are platform-specific; the Demo enables only packs whose runtime is installed."
