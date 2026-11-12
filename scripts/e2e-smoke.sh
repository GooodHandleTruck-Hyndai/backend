#!/usr/bin/env bash
# 로컬 e2e 점검 (#55 에서 추가)
#
# compose.e2e.yaml 로 띄운 서버에 앱 사용 순서대로 요청, 하나라도 이상하면 실패 종료
#   ./scripts/e2e-smoke.sh                       # 기본 http://localhost:8080
#   BASE=http://10.0.0.5:8080 ./scripts/e2e-smoke.sh
#
# 필요: bash, curl (Windows 는 Git Bash). 응답은 JSON 문자열 검사만 → jq 불필요
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
# 마포 공덕역 근처, 북쪽을 보고 서 있는 차
LAT=37.5440
LON=126.9515
PASS=0
FAIL=0

check() { # check <이름> <조건 결과(0=통과)> <보여 줄 값>
  if [ "$2" -eq 0 ]; then PASS=$((PASS + 1)); echo "  통과  $1"; else FAIL=$((FAIL + 1)); echo "  실패  $1  ($3)"; fi
}
has() { grep -q -- "$2" <<<"$1"; echo $?; }
first_id() { grep -o "\"$2\":[0-9]*" <<<"$1" | head -1 | cut -d: -f2; }

echo "점검 대상: $BASE"

# 0) 서버 준비까지 최대 3분 대기 (첫 실행은 이미지 빌드·DB 시드로 오래 걸림)
for _ in $(seq 1 90); do curl -s -m 2 "$BASE/health" | grep -q ok && break; sleep 2; done
H=$(curl -s -m 5 "$BASE/health"); check "서버 상태 /health" "$(has "$H" '"ok"')" "$H"

# 1) 첫 실행: 기기 등록 → 익명 ID
D=$(curl -s -m 10 -X POST -H 'Content-Type: application/json' -d '{}' "$BASE/api/v1/devices")
DEV=$(grep -o '"anonymousId":"[^"]*"' <<<"$D" | cut -d'"' -f4)
check "기기 등록" "$([ -n "$DEV" ]; echo $?)" "$D"
HDR=(-H "X-Anonymous-Id: $DEV")

# 2) 현재 위치 지역 (카카오 키 없으면 DB 로 대체)
R=$(curl -s -m 10 "${HDR[@]}" "$BASE/api/v1/regions?lat=$LAT&lon=$LON")
check "현재 위치 지역 = 마포구(11440)" "$(has "$R" '"lawdCd":"11440"')" "$R"

# 3) 주변 건물 → 첫 건물 상세 → 거래 내역 → 가격 추이
N=$(curl -s -m 20 "$BASE/api/v1/buildings/nearby?lat=$LAT&lon=$LON&radius=1000")
BID=$(first_id "$N" buildingId)
check "주변 건물(반경 1km)" "$([ -n "$BID" ]; echo $?)" "${N:0:200}"
DT=$(curl -s -m 10 "${HDR[@]}" "$BASE/api/v1/buildings/$BID")
check "건물 상세" "$(has "$DT" '"recentTransactions"')" "${DT:0:200}"
TX=$(curl -s -m 10 "$BASE/api/v1/buildings/$BID/transactions?page=0&size=5")
check "실거래 내역" "$(has "$TX" '"transactions"')" "${TX:0:200}"
PT=$(curl -s -m 10 "$BASE/api/v1/buildings/$BID/price-trend")
check "가격 추이" "$(has "$PT" '"months"')" "${PT:0:200}"

# 4) 주행 중 [주변 단지 듣기] → 저장 → 다시 들으면 저장됨
I=$(curl -s -m 10 "${HDR[@]}" "$BASE/api/v1/buildings/identify?lat=$LAT&lon=$LON&heading=0")
IID=$(first_id "$I" buildingId)
check "앞쪽 건물 찾기 + 안내 문장" "$([ -n "$IID" ] && grep -q '"speechText"' <<<"$I"; echo $?)" "${I:0:200}"
S=$(curl -s -m 10 -X POST "${HDR[@]}" -H 'Content-Type: application/json' -d "{\"buildingId\":$IID}" "$BASE/api/v1/favorites")
check "들은 건물 저장" "$(has "$S" '"success":true')" "${S:0:200}"
I2=$(curl -s -m 10 "${HDR[@]}" "$BASE/api/v1/buildings/identify?lat=$LAT&lon=$LON&heading=0")
check "다시 들으면 saved=true" "$(has "$I2" '"saved":true')" "${I2:0:200}"
F=$(curl -s -m 10 "${HDR[@]}" "$BASE/api/v1/favorites")
check "저장 목록에 있음" "$(has "$F" "\"buildingId\":$IID")" "${F:0:200}"

# 5) 관심 조건 저장 → 알림함
C=$(curl -s -m 10 -X PUT "${HDR[@]}" -H 'Content-Type: application/json' \
  -d '{"regionCodes":["11440"],"priceMin":80000,"priceMax":150000,"types":["APT"],"areaMinM2":59,"areaMaxM2":85,"alertEnabled":true}' \
  "$BASE/api/v1/interest-conditions")
check "관심 조건 저장" "$(has "$C" '"success":true')" "${C:0:200}"
A=$(curl -s -m 10 "${HDR[@]}" "$BASE/api/v1/alerts")
check "알림함 열기" "$(has "$A" '"alerts"')" "${A:0:200}"

echo
echo "통과 $PASS, 실패 $FAIL"
[ "$FAIL" -eq 0 ]
