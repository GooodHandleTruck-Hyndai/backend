#!/usr/bin/env bash
# 로컬 e2e 시드 데이터 재생성 (#55 에서 추가)
#
# - 개발용 MySQL(compose.yaml, 컨테이너 zippick-backend-mysql-1)의 마포 실거래 데이터 → e2e/seed/01-zippick.sql
# - compose.e2e.yaml 의 MySQL 첫 실행 때 이 파일 적재
#
#   ./scripts/e2e-dump-seed.sh            # 개발 서버로 데이터 수집 완료 후 (사전 저장·재수집·좌표 채우기)
#
# 넣는 것: 건물·거래·법정동·수집 이력 (전부 국토부·행안부 공공데이터에서 온 값)
# 빼는 것: 기기·저장·관심 조건·알림(테스트하며 만든 개인 기록), 좌표 백업 표
# 카카오 좌표는 정책상 DB 저장 안 함 → 시드에도 없음 (coord_source=JUSO 좌표만)
set -euo pipefail

CONTAINER="${CONTAINER:-zippick-backend-mysql-1}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/e2e/seed/01-zippick.sql"
TABLES=(legal_dongs buildings trades collect_runs)

# docker 명령 이름 (WSL 에서 Docker Desktop 을 쓰면 docker.exe)
DOCKER=docker
command -v docker >/dev/null 2>&1 || DOCKER=docker.exe

ROOT_PW="$(grep -m1 'MYSQL_ROOT_PASSWORD' "$(dirname "$0")/../compose.yaml" | awk -F': ' '{print $2}' | tr -d '"' | tr -d '\r')"

mkdir -p "$(dirname "$OUT")"
{
  echo "-- 집픽 로컬 e2e 시드 (scripts/e2e-dump-seed.sh 로 생성, $(date '+%Y-%m-%d'))"
  echo "-- 출처: 국토교통부 실거래가 공개 자료, 행정안전부 주소정보 (공공데이터포털)"
  echo "SET NAMES utf8mb4;"
  "$DOCKER" exec "$CONTAINER" mysqldump -uroot -p"$ROOT_PW" \
    --default-character-set=utf8mb4 --single-transaction --no-tablespaces \
    --skip-dump-date --skip-comments --set-gtid-purged=OFF \
    zippick "${TABLES[@]}" 2>/dev/null
} > "$OUT"

echo "저장: $OUT ($(du -h "$OUT" | cut -f1))"
# 표별 행 수 (개발 DB 기준 = 시드와 동일)
for t in "${TABLES[@]}"; do
  printf '  %-13s %s행\n' "$t" "$("$DOCKER" exec "$CONTAINER" mysql -uroot -p"$ROOT_PW" -N zippick -e "select count(*) from $t" 2>/dev/null | tr -d '\r')"
done
