# 서버 배포 (AWS EC2)

공모전 심사용 공개 서버. 앱(정식 APK)이 붙는 https 주소 제공 (#57)

- 주소: `https://3-37-254-181.sslip.io` (확인: `/health`, `/swagger-ui.html`)
- 운영 기간: 1차 심사 결과 발표(11/23)까지 → 이후 중지 예정

## 구성

| 항목 | 값 |
|---|---|
| 서버 | EC2 `zippick-backend` (t3.small, Ubuntu 24.04, gp3 20GB, 서울) + 스왑 2GB |
| 고정 IP | `3.37.254.181` (Elastic IP) |
| 보안 그룹 | `zippick-backend-sg`: 80·443 전체 공개, 22 관리 PC IP 만 |
| 실행 | `compose.prod.yaml`: MySQL 8.4 + 서버 + Caddy (https 인증서 자동 발급·갱신, Let's Encrypt) |
| 이미지 | ECR `zippick-backend` (최근 5개만 보관) |
| 자동 배포 | `.github/workflows/deploy-ec2.yml` |
| AWS 자원 표시 | 태그 `Project=zippick` |

사양 근거 (t3.small 선택): 로컬 실측(#57 PR)
- 대기: 서버 약 400MB + MySQL 약 475MB
- 동시 20명 부하, 2GB 기준 제한(서버 800MB·DB 600MB): 오류 0, 메모리 부족 없음
- 1GB 기준 제한(서버 450MB·DB 350MB): MySQL 메모리 부족 강제 종료 → t3.micro 불가

## 자동 배포 흐름

```text
main 머지 → Backend CI 성공 → (Deploy to EC2)
  → 이미지 빌드 → ECR 업로드
  → SSM 으로 EC2 에 명령: 코드 갱신(git) → 새 이미지 받기 → 서버 컨테이너 교체
  → https /health 확인
```

- AWS 인증: OIDC 역할 `zippick-github-deploy` (이 저장소 main 에서만 사용 가능, GitHub 에 AWS 키 없음)
- 교체 중 약 20초 동안 502 응답 (서버 재시작 시간) → 시연 중에는 머지 피하기
- 수동 재배포: Actions 탭 → "Deploy to EC2" → Run workflow (main)

## 서버 .env (저장소에 올리지 않음)

EC2 `~/zippick-backend/.env` (권한 600)

```text
SITE_ADDRESS=3-37-254-181.sslip.io
DB_PASSWORD=…              # 임의 생성 값
DB_ROOT_PASSWORD=…
KAKAO_REST_API_KEY=…
MOLIT_SERVICE_KEY=…
ZIPPICK_REFRESH_ENABLED=true   # 매일 04:00 최근 3개월 재수집
ZIPPICK_ALERT_ENABLED=true     # 04:30 새 거래 알림 생성
ZIPPICK_JUSO_FILL_ENABLED=false # 행안부 개발키 만료가 잦아서 끔 (좌표는 시드에 이미 있음)
ZIPPICK_PRELOAD_ENABLED=false
```

- 값 바꾼 뒤 반영: `docker compose -f compose.prod.yaml up -d`
- 첫 실행 데이터: `e2e/seed/01-zippick.sql` (DB 볼륨이 비어 있을 때만 적재)

## 자주 쓰는 명령 (EC2)

```bash
ssh -i ~/.ssh/zippick-ec2.pem ubuntu@3.37.254.181
cd ~/zippick-backend
docker compose -f compose.prod.yaml ps
docker compose -f compose.prod.yaml logs -f --tail 100 app
cat DEPLOYED_VERSION                        # 배포 기록 (시각, 커밋)
```

- 로그: 컨테이너 재시작 후에도 journald 에 남음 → `journalctl CONTAINER_NAME=zippick-app-1 --since today`
- 관리 PC IP 가 바뀌면 보안 그룹 22번 규칙 수정 필요

## 되돌리기

- 이전 이미지로: Actions 에서 이전 커밋으로 수동 실행, 또는 EC2 에서
  `APP_IMAGE=<ECR 주소>:<이전 커밋> docker compose -f compose.prod.yaml up -d --no-build`
- 설정만: `.env` 수정 후 `up -d`

## 끄기 (심사 후)

| 방법 | 비용 | 주소 |
|---|---|---|
| 중지 (`aws ec2 stop-instances --instance-ids i-0bb4c3db66531870e`) | 디스크·고정 IP 만 (월 약 $5) | 유지 |
| 삭제 (인스턴스 종료 + 고정 IP 반납 + ECR·역할 삭제) | 0 | 바뀜 → 앱 주소 변경 필요 |

- 삭제할 자원은 태그 `Project=zippick` 로 찾기: EC2·볼륨·Elastic IP·보안 그룹·키 페어·ECR, IAM 역할 `zippick-ec2-role`·`zippick-github-deploy`, 인스턴스 프로필 `zippick-ec2-profile`
