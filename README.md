# 시동 (Sidong) 백엔드

> GooodHandleTruck · 주행 중 운전자를 돕는 차량용 서비스의 API 서버

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
2. `SidongApplication` 을 실행(▶)해요.
   - Spring Boot 가 `compose.yaml` 의 MySQL 컨테이너를 **자동으로 띄워요.** DB 를 따로 설치할 필요가 없어요.
3. 브라우저에서 확인해요.
   - 서버 상태: http://localhost:8080/health → `{"status":"ok"}`
   - API 문서: http://localhost:8080/swagger-ui.html

### 터미널로 실행하려면
```bash
./gradlew bootRun        # Windows PowerShell: .\gradlew.bat bootRun
./gradlew build          # 컴파일 + 테스트
```

### 로컬 DB 접속 정보
| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `3307` (PC에 깔린 MySQL 과 안 겹치게) |
| Database | `sidong` |
| User / Password | `sidong` / `sidong` |

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
@GetMapping("/trips/{id}")
public ApiResponse<TripResponse> getTrip(@PathVariable Long id) {
    return ApiResponse.ok(tripService.getTrip(id));
}
```

### 에러 처리
서비스에서 예외를 던지기만 하면, `GlobalExceptionHandler` 가 알맞은 상태 코드와 실패 응답으로 바꿔줘요.
```java
throw new BusinessException(ErrorCode.NOT_FOUND);
```
새 에러가 필요하면 `ErrorCode` 에 추가해요. 공통 에러는 `C`, 도메인 에러는 도메인마다 앞 글자를 정해요(예: 주행 기록 `T001`).

| 상황 | HTTP 상태 | code |
|---|---|---|
| `@Valid` 검증 실패 | 400 | C001 |
| 없는 경로 / 리소스 | 404 | C002 |
| 지원하지 않는 메서드 | 405 | C003 |
| 예상 못 한 서버 오류 | 500 | C999 |

---

## 📁 폴더 구조

```
src/main/java/com/gooodhandletruck/sidong/
├── SidongApplication.java
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
