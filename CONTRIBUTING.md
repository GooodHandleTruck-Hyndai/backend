# 시동 협업 가이드

GooodHandleTruck 팀이 같은 방식으로 일하기 위한 규칙이에요. 짧게 유지하고, 바꾸고 싶은 규칙이 있으면 PR로 제안해 주세요.

---

## 작업 흐름 한눈에 보기

```
이슈 만들기 → 브랜치 따기 → 작업 & 커밋 → PR → 리뷰 1명 승인 → Squash 머지 → 이슈 자동 닫힘
```

1. **이슈부터 만들어요.** 작은 작업이라도 이슈 번호가 있어야 브랜치, 커밋, PR이 서로 연결돼요.
2. **main 에서 브랜치를 따요.**
   ```bash
   git switch main && git pull
   git switch -c feature/#12-trip-api
   ```
3. **작업하고 커밋해요.** 커밋 규칙은 아래를 보세요.
4. **PR 을 올려요.** 템플릿의 `Closes #12` 에 이슈 번호를 꼭 적어요.
5. **리뷰어 1명이 승인하면 Squash and merge** 로 합쳐요. 머지한 브랜치는 삭제해요.

---

## 브랜치 이름

`종류/#이슈번호-짧은-설명` (영어 소문자, 단어는 하이픈으로 연결)

| 종류 | 언제 | 예시 |
|---|---|---|
| `feature` | 새 기능 | `feature/#12-trip-api` |
| `fix` | 버그 수정 | `fix/#15-trip-n-plus-one` |
| `refactor` | 동작 변화 없는 코드 개선 | `refactor/#20-split-trip-service` |
| `chore` | 빌드, 설정, 의존성 | `chore/#3-swagger-setup` |
| `docs` | 문서 | `docs/#5-readme` |

- `main` 에는 직접 push 하지 않아요. 항상 PR로 들어가요.

---

## 커밋 메시지

```
타입: 무엇을 했는지 (한국어 OK)

필요하면 본문에 이유를 적어요.
```

| 타입 | 뜻 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩터링 |
| `style` | 포맷, 세미콜론 등 (동작 변화 없음) |
| `docs` | 문서 |
| `test` | 테스트 |
| `chore` | 빌드, 설정, 의존성 |
| `ci` | GitHub Actions |

예시
```
feat: 주행 기록 저장 API 추가
fix: 주행 기록 조회 시 N+1 쿼리 수정
chore: Spring Boot 4.0.9 로 업데이트
```

> PR은 Squash 머지라서, **PR 제목**이 main 에 남는 최종 커밋 메시지가 돼요. PR 제목도 같은 규칙으로 써 주세요.

---

## 이슈

이슈는 **내가 할 일을 내가 등록하는 것**이에요. 작업을 시작하기 전에 이슈부터 만들어요.

- 템플릿을 골라요: ✨ 기능(feat) / 🐛 버그 수정(fix) / 🔧 리팩터링·설정(refactor, chore)
- **Assignee 에 본인을 지정**해요. (오른쪽 사이드바 Assignees → assign yourself)
- TODO 체크리스트는 작업하면서 하나씩 체크해요. 진행 상황이 그대로 보여요.
- 라벨은 템플릿이 종류 라벨을 자동으로 붙여 줘요. 영역 라벨(`android`, `backend`)만 하나 더 붙여요.
- 하루 넘게 걸릴 것 같으면 이슈를 더 잘게 쪼개요.
- 다른 사람에게 부탁할 일이면 같은 템플릿으로 만들고, Assignee 를 그 사람으로 지정해요.

---

## PR 과 리뷰

- PR 하나에는 이슈 하나만 담아요. 가능하면 변경 400줄 이하로 유지해요.
- API 가 바뀌면 PR 템플릿의 "API 변경" 표를 채우고, 앱 담당자를 리뷰어로 넣어요.
- 리뷰 코멘트 앞에 말머리를 붙이면 의도가 명확해져요.
  - `[필수]` 머지 전에 꼭 고쳐야 함
  - `[제안]` 고치면 좋지만 작성자 판단
  - `[질문]` 궁금해서 묻는 것
- 리뷰 요청은 24시간 안에 봐 주기로 해요.

---

## 코드 구조와 스타일

- 들여쓰기, 줄 길이 같은 기본 포맷은 `.editorconfig` 가 정해요. IntelliJ 에서 커밋 전에 **Ctrl+Alt+L**(코드 정리), **Ctrl+Alt+O**(import 정리)를 눌러 주세요.
- 패키지 구조
  ```
  com.gooodhandletruck.sidong
  ├── global/          공통 (응답 형식, 예외 처리, 설정)
  └── domain/
      └── trip/        기능 단위로 패키지를 나눠요
          ├── controller/  API 입구 (@RestController)
          ├── service/     비즈니스 로직 (@Service)
          ├── repository/  DB 접근 (JpaRepository)
          ├── entity/      DB 테이블 (@Entity)
          └── dto/         요청/응답 (record)
  ```
- 규칙
  - 응답은 `ApiResponse.ok(data)` 로 감싸요.
  - 에러는 `throw new BusinessException(ErrorCode.XXX)` 로 던져요. 새 에러가 필요하면 `ErrorCode` 에 추가해요.
  - 엔티티를 그대로 응답으로 내보내지 않아요. 꼭 DTO 로 바꿔요.
  - 컨트롤러에는 로직을 넣지 않아요. Service 에서 처리해요.
  - API 를 만들면 `@Operation(summary = "...")` 를 달아서 Swagger 에 설명이 보이게 해요.
- 커밋 전에 한 번:
  ```bash
  ./gradlew build     # 컴파일 + 테스트 (CI에서도 이걸 돌려요)
  ```
  Windows PowerShell 에서는 `.\gradlew.bat build` 예요.

---

## 절대 커밋하면 안 되는 것

- 운영 DB 비밀번호, API 키, JWT 시크릿 같은 비밀값
  - 환경 변수로 넣고 `application.yml` 에서는 `${DB_PASSWORD}` 처럼 읽어요.
  - 로컬 전용 비밀값은 `.env` 나 `application-secret.yml` 에 넣어요. (둘 다 `.gitignore` 에 막혀 있어요)

`.gitignore` 에 이미 막혀 있지만, `git add .` 하기 전에 `git status` 로 한 번 확인해 주세요.
