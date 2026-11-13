# 집픽 서버 이미지 (#55 에서 추가)
# - 로컬 e2e(compose.e2e.yaml)·서버 배포 공용
# - 이미지 안에서 빌드 → 실행 PC 에 JDK 불필요
#   docker build -t zippick-backend .

# 1) 빌드: Gradle 래퍼로 실행 jar 생성 (테스트는 CI·로컬 build 에서 수행 → 여기선 생략)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# 의존성 단계 분리 → 코드만 바뀌면 의존성 재다운로드 없음
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" app.jar

# 2) 실행: JRE 만 담은 이미지
FROM eclipse-temurin:17-jre
# 저장 시각(LocalDateTime.now())이 서버 시간대 기준, 응답은 KST 가정 → KST 고정
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
