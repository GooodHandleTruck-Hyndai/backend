# 집픽 (Zippick) 백엔드

> GooodHandleTruck · 차 안에서 내 주변 건물 시세를 확인하는 플레오스 앱의 API 서버

- Spring Boot 4.0 · Java 17 · MySQL 8.4 · JPA · Swagger
- 협업 규칙(브랜치, 커밋, PR): [CONTRIBUTING.md](CONTRIBUTING.md)
- 차량 앱 저장소: [frontend](https://github.com/GooodHandleTruck-Hyndai/frontend)

---

## 실행하기

### 준비물
- **IntelliJ IDEA**
- **JDK 17**: 없으면 IntelliJ 에서 File → Project Structure → SDK → Download JDK → 17
- **Docker Desktop**: 로컬 MySQL 을 띄울 때 필요해요. 설치하고 **켜 둔 상태**여야 해요.

### 실행
1. IntelliJ → **Open** → 이 폴더를 열고 Gradle Sync 가 끝날 때까지 기다려요.
2. `ZippickApplication` 을 실행(▶)해요.
   - Spring Boot 가 `compose.yaml` 의 MySQL 컨테이너를 **자동으로 띄워요.** DB 를 따로 설치할 필요가 없어요.
3. 브라우저에서 확인해요.
   - 서버 상태: http://localhost:8080/health → `{"status":"ok"}`
   - API 문서: http://localhost:8080/swagger-ui.html

### 터미널로 실행하려면
```bash
./gradlew bootRun        # Windows PowerShell: .\gradlew.bat bootRun
./gradlew build          # 컴파일 + 테스트
```

### Docker 로 한 번에 띄우기 (심사·시연용, #55)
JDK·IntelliJ 없이 **Docker Desktop 만 있으면** MySQL + 서버 + 마포 시연 데이터가 한 번에 떠요. API 키도 필요 없어요.

```bash
docker compose -f compose.e2e.yaml up --build -d   # 처음엔 서버 이미지 빌드로 3~5분
./scripts/e2e-smoke.sh                             # 앱이 쓰는 흐름 13가지 점검 (Windows 는 Git Bash)
docker compose -f compose.e2e.yaml down -v         # 끄기 + 데이터 지우기
```

- 확인: http://localhost:8080/health , http://localhost:8080/swagger-ui.html
- **안드로이드 에뮬레이터 앱(디버그 APK)** 은 `http://10.0.2.2:8080` = 이 PC 의 `localhost:8080` 으로 바로 붙어요.
- 데이터: `e2e/seed/01-zippick.sql` (마포구 건물·실거래, 국토교통부·행정안전부 공공데이터). 다시 뽑기: `./scripts/e2e-dump-seed.sh`
- 카카오 키(선택): 레포 폴더에 `.env` 파일을 만들고 `KAKAO_REST_API_KEY=...` 를 넣으면 지역 찾기·좌표 없는 건물에 카카오를 써요.
  없으면 지역은 DB(가장 가까운 건물의 구·동)로 대신 찾고, 행안부 좌표가 없는 일부 건물은 지도에 안 나와요.
- 개발용 `compose.yaml` 과 프로젝트 이름·볼륨이 달라서 서로 영향이 없어요. 8080 포트를 쓰는 다른 서버는 먼저 꺼 주세요.

### 로컬 DB 접속 정보
| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `3307` (PC에 깔린 MySQL 과 안 겹치게) |
| Database | `zippick` |
| User / Password | `zippick` / `zippick` |

IntelliJ 의 Database 탭이나 DBeaver 로 붙어서 볼 수 있어요. 데이터는 Docker 볼륨에 남아서, 서버를 껐다 켜도 유지돼요.

---
## 📐 API 규칙

### 공통 응답 형식
모든 API 는 `ApiResponse` 로 감싸서 응답해요. (`/health` 만 예외)

```json
// 성공
{ "success": true, "code": "OK", "message": "요청 성공", "data": { "id": 1 } }

// 실패
{ "success": false, "code": "C001", "message": "name: 비어 있을 수 없습니다" }
```

```java
@GetMapping("/buildings/{buildingId}")
public ApiResponse<BuildingResponse> getBuilding(@PathVariable String buildingId) {
    return ApiResponse.ok(buildingService.getBuilding(buildingId));
}
```

### 에러 처리
서비스에서 예외를 던지기만 하면, `GlobalExceptionHandler` 가 알맞은 상태 코드와 실패 응답으로 바꿔줘요.
```java
throw new BusinessException(ErrorCode.NOT_FOUND);
```
새 에러가 필요하면 `ErrorCode` 에 추가해요. 공통 에러는 `C`, 도메인 에러는 도메인마다 앞 글자를 정해요(예: 저장 `F001`).

| 상황 | HTTP 상태 | code |
|---|---|---|
| `@Valid` 검증 실패 | 400 | C001 |
| 없는 경로 / 리소스 | 404 | C002 |
| 지원하지 않는 메서드 | 405 | C003 |
| 예상 못 한 서버 오류 | 500 | C999 |

---

## 📁 폴더 구조

```
src/main/java/com/gooodhandletruck/zippick/
├── ZippickApplication.java
├── global/
│   ├── response/ApiResponse.java          공통 응답 형식
│   ├── error/                             ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── config/                            Swagger, JPA Auditing 설정
│   └── entity/BaseTimeEntity.java         createdAt / updatedAt 자동 기록
├── health/HealthController.java           GET /health (앱 연결 확인용)
└── domain/                                기능별 패키지 (package-info.java 참고)

src/main/resources/
├── application.yml                        공통 설정
└── application-local.yml                  로컬 개발용 (MySQL 3307)
src/test/resources/application.yml         테스트용 (H2 메모리 DB)
compose.yaml                               로컬 MySQL 컨테이너
```
