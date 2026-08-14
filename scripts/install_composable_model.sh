#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  install_composable_model.sh ARTIFACT [--base-url HTTPS_URL]
      [--language CODE] [--voice ID]

The acoustic core and catalog are always bundled in the APK. Language and
voice packs are optional; selected packs become built-in defaults. Remaining
ZIP files under ARTIFACT/composable/packages can be hosted for HTTPS download.
HTTPS_URL must point at the hosted composable/ directory.
EOF
  exit 2
}

[[ $# -ge 1 ]] || usage
SOURCE="$(cd "$1" && pwd)"
shift
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/app/src/main/assets/tts"
COMPOSABLE="$SOURCE/composable"

[[ -f "$COMPOSABLE/catalog.json" ]] || {
  echo "Invalid export: missing composable/catalog.json" >&2
  exit 1
}
[[ -f "$COMPOSABLE/core/model.onnx" ]] || {
  echo "Invalid export: missing composable/core/model.onnx" >&2
  exit 1
}

languages=()
voices=()
base_url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || usage
      base_url="$2"
      shift 2
      ;;
    --language)
      [[ $# -ge 2 ]] || usage
      languages+=("$2")
      shift 2
      ;;
    --voice)
      [[ $# -ge 2 ]] || usage
      voices+=("$2")
      shift 2
      ;;
    *) usage ;;
  esac
done

mkdir -p "$TARGET"
find "$TARGET" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
mkdir -p "$TARGET/composable"
cp "$COMPOSABLE/catalog.json" "$TARGET/composable/"
cp -R "$COMPOSABLE/core" "$TARGET/composable/"
if [[ -n "$base_url" ]]; then
  [[ "$base_url" == https://* ]] || {
    echo "--base-url must use HTTPS" >&2
    exit 1
  }
  [[ "$base_url" == */ ]] || base_url="$base_url/"
  printf '{\n  "base_url": "%s"\n}\n' "$base_url" \
    > "$TARGET/pack-repository.json"
fi

for language in "${languages[@]}"; do
  [[ -f "$COMPOSABLE/languages/$language/manifest.json" ]] || {
    echo "Unknown language pack: $language" >&2
    exit 1
  }
  mkdir -p "$TARGET/composable/languages"
  cp -R "$COMPOSABLE/languages/$language" \
    "$TARGET/composable/languages/"
done

for voice in "${voices[@]}"; do
  [[ -f "$COMPOSABLE/voices/$voice/manifest.json" ]] || {
    echo "Unknown voice pack: $voice" >&2
    exit 1
  }
  mkdir -p "$TARGET/composable/voices"
  cp -R "$COMPOSABLE/voices/$voice" "$TARGET/composable/voices/"
done

ESPEAK="$SOURCE/frontend-packs/_shared/espeak-ng/espeak-ng-data"
if [[ -d "$ESPEAK" ]]; then
  mkdir -p "$TARGET/runtime"
  cp -R "$ESPEAK" "$TARGET/runtime/"
fi

echo "Composable acoustic core installed: $TARGET/composable/core"
echo "Built-in language packs: ${languages[*]:-none}"
echo "Built-in voice packs: ${voices[*]:-none}"
echo "Host downloadable ZIPs from: $COMPOSABLE/packages"
echo "Pack repository base URL: ${base_url:-not configured}"
