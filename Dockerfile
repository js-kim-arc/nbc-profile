# 결정 근거: docs/adr/0011-multistage-docker-build-strategy.md

# ============================================================
# 1단계: builder — JDK + Gradle Wrapper 로 fat JAR 생성
# ============================================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# 의존성 레이어 캐시: gradle 메타 파일을 *먼저* COPY → 소스만 바뀐 재빌드 시 deps 재사용
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# gradlew 의 라인엔딩이 CRLF 일 때 컨테이너 (Linux) 에서 'bash\r' 오류 — 안전망
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 의존성만 먼저 해소 (소스 변경에 영향받지 않는 레이어)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 COPY 후 패키징 — 테스트는 CI 책임이므로 -x test (ADR-0011 §Decision)
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v 'plain\.jar$')" /workspace/app.jar

# ============================================================
# 2단계: runtime — JRE 만으로 jar 실행 (compiler · src · gradle 캐시 미포함)
# ============================================================
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# builder 에서 plain jar 분리 후 /workspace/app.jar 로 단일화 (version 변경 무관)
COPY --from=builder /workspace/app.jar /app/app.jar

# 메타데이터 (실제 포트 매핑은 docker run -p 가 결정)
EXPOSE 8080

# exec form → java 가 PID 1 → SIGTERM 직결
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
