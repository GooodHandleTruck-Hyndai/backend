#!/usr/bin/env bash
# GitHub 라벨을 한 번에 만들어요. (GitHub CLI 필요: https://cli.github.com)
# 사용법: 저장소 폴더에서  bash scripts/setup-labels.sh
set -euo pipefail

label() { gh label create "$1" --color "$2" --description "$3" --force; }

# 종류
label "feat"     "1D76DB" "새 기능"
label "bug"      "D73A4A" "버그"
label "refactor" "5319E7" "동작 변화 없는 코드 개선"
label "docs"     "0075CA" "문서"
label "chore"    "C5DEF5" "빌드, 설정, 의존성"
label "design"   "F9A8D4" "UI/UX"

# 영역
label "android"  "3DDC84" "차량 앱 (Kotlin)"
label "backend"  "FBCA04" "서버"

# 상태 / 우선순위
label "priority: high" "B60205" "급함"
label "blocked"        "000000" "다른 작업 때문에 진행 불가"
label "question"       "D876E3" "논의 필요"

echo "✅ 라벨 세팅 완료"
