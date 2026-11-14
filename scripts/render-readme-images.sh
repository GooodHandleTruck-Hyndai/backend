#!/usr/bin/env bash
# README 그림 생성 (#59)
# - 원본: docs/assets/src/*.html (차량 앱 색·Asta Sans 글꼴)
# - 결과: docs/assets/{hero,flow,architecture,deploy}.png (2배 해상도, 투명 배경)
# - 글꼴: 차량 앱 저장소의 app/src/main/res/font/asta_sans.ttf 를 잠깐 복사해 사용 (저장소에는 안 올림, SIL OFL 1.1)
# - 브라우저: BROWSER 환경 변수 → 없으면 google-chrome / chromium → WSL 이면 Windows Edge
#
# 사용: scripts/render-readme-images.sh <차량 앱 저장소 경로>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRONT="${1:?차량 앱(frontend) 저장소 경로 필요}"
FONT="$FRONT/app/src/main/res/font/asta_sans.ttf"
SRC="$ROOT/docs/assets/src"
OUT="$ROOT/docs/assets"
BUILD="$SRC/.build"   # 렌더링용 임시 폴더 (끝나면 삭제)

# 그림 이름과 크기(CSS 픽셀). HTML 의 body 크기와 같아야 함
IMAGES=("hero 1280 560" "flow 1200 380" "architecture 1200 620" "deploy 1200 600")

# 사용할 브라우저 실행 파일 찾기
find_browser() {
  if [ -n "${BROWSER:-}" ]; then echo "$BROWSER"; return; fi
  for b in google-chrome chromium chromium-browser; do
    if command -v "$b" >/dev/null 2>&1; then command -v "$b"; return; fi
  done
  local edge="/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
  if [ -x "$edge" ]; then echo "$edge"; return; fi
  echo "Chrome·Edge 를 찾지 못함 (BROWSER 로 지정)" >&2; exit 1
}

# 브라우저에 넘길 경로로 변환 (WSL 의 Windows 브라우저면 Windows 경로)
to_browser_path() {
  if [[ "$BROWSER_BIN" == *.exe ]]; then wslpath -w "$1"; else echo "$1"; fi
}

# HTML 한 장을 PNG 로 렌더링
render() {
  local name="$1" w="$2" h="$3"
  local html png url
  html="$(to_browser_path "$BUILD/$name.html")"
  png="$(to_browser_path "$BUILD/$name.png")"
  url="file:///$(echo "$html" | tr '\\' '/' | sed 's#^/##')"
  "$BROWSER_BIN" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
    --default-background-color=00000000 --window-size="$w,$h" --screenshot="$png" "$url" >/dev/null 2>&1
  cp "$BUILD/$name.png" "$OUT/$name.png"
  echo "  $name.png (${w}×${h})"
}

[ -f "$FONT" ] || { echo "글꼴 없음: $FONT" >&2; exit 1; }
BROWSER_BIN="$(find_browser)"
rm -rf "$BUILD"; mkdir -p "$BUILD/fonts"
trap 'rm -rf "$BUILD"' EXIT
cp "$SRC"/*.html "$SRC"/*.css "$BUILD/"
cp "$FONT" "$BUILD/fonts/asta_sans.ttf"

echo "README 그림 생성 → docs/assets/"
for item in "${IMAGES[@]}"; do
  # shellcheck disable=SC2086
  render $item
done
