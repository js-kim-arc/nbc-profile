# ADR-0011: 멀티스테이지 Docker 빌드 + eclipse-temurin base 채택

- **Status**: Proposed
- **Date**: 2026-05-23
- **Deciders**: junseong kim
- **Trigger**: T1 (멀티스테이지 vs 단일스테이지 vs Jib/bootBuildImage), T4 (Docker · eclipse-temurin 도입), T7 (Distroless · Alpine · Jib · Compose · ECR · JVM 튜닝 연기)

## Context

Product 2 (VPC + EC2) 까지 진행되어 EC2 인스턴스 (`3.36.121.211`, t3.micro AL2023) + systemd 유닛 (`deploy/profile-app.service`) 까지 마련됨 — 그러나 *애플리케이션 패키징* 은 여전히 *로컬 JDK 의존 fat JAR* 상태.

배포 force:
- 로컬(Windows + JDK 21) ↔ EC2(AL2023) 간 *시스템 라이브러리 · JDK 마이너 버전* 차이가 배포 장애의 흔한 원인.
- *Docker Hub Push/Pull* 의 회선·시간 비용 → 이미지 크기가 클수록 배포 사이클이 늘어남.
- t3.micro (1GB RAM) 환경 — *런타임 메모리 footprint* 와 *디스크 footprint* 동시 압박.
- 빌드 도구 (JDK + Gradle 캐시) 를 런타임 이미지에 함께 포함하면 *보안 표면* + *이미지 크기* 동시 악화.

본 결정 이전:
- ADR-0008 (profile-based yml split) — 환경별 *구조 차이* 는 yml, *시크릿* 은 환경변수.
- ADR-0010 (actuator) — `/actuator/health` 만 노출 + `show-details: never`.
- 두 ADR 의 정책은 *컨테이너 안에서도 그대로 유지* 되어야 함 — Docker 자체가 추가 정책을 *덮어쓰지 않도록* 설계.

## Decision

**멀티스테이지 Dockerfile + `eclipse-temurin:21-jdk` (builder) → `eclipse-temurin:21-jre` (runtime) + 의존성 레이어 분리 캐시** 채택:

```Dockerfile
# ---- builder ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 레이어 캐시: gradle 메타 파일 먼저 COPY
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies || true

# 소스 COPY 후 bootJar (test 는 CI 책임 — 본 Dockerfile 은 패키징 책임만)
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

핵심 규칙:
- **base = eclipse-temurin**: Adoptium 공식 · OpenJDK 직접 빌드 · 풍부한 태그 · Spring Boot reference 권장.
- **builder JDK → runtime JRE**: 런타임은 컴파일러 · javadoc · src.zip · Gradle 캐시 미포함 → 이미지 크기 + 보안 표면 동시 축소.
- **의존성 레이어 분리**: `gradlew` + `gradle/` + `*.gradle.kts` 만 먼저 COPY → `./gradlew dependencies` 캐시 → 소스만 바뀐 재빌드는 *의존성 다운로드 스킵*.
- **`-x test`**: Dockerfile 은 *패키징 책임만*. 테스트는 CI 단계.
- **exec form `ENTRYPOINT`**: `java` 가 PID 1 — 신호(SIGTERM) 전달이 shell wrapper 없이 직결.
- **`COPY *.jar`**: `version 0.0.1-SNAPSHOT` 변경에도 깨지지 않게 와일드카드. builder 의 `build/libs` 에 다른 jar 가 끼지 않도록 `bootJar` 단독 호출.

베이스 태그 정책:
- `eclipse-temurin:21-jdk` / `:21-jre` (Ubuntu 기반, *non-Alpine*) — `glibc` 의존성 안전.
- 마이너 버전 핀 (`:21.0.x-jdk`) 은 *재현성 vs 보안 패치 자동 수용* 트레이드오프 → *본 PR 은 메이저만 핀* (`:21-jdk`). 재검토는 보안 인시던트 발생 시.

## Alternatives Considered

### Option A. 단일스테이지 (`FROM eclipse-temurin:21-jdk` 전체)

- **Pros**: Dockerfile 1줄 단순.
- **Cons**: 런타임에 JDK + Gradle 캐시 + 소스가 모두 잔존. 이미지 크기 ~2배. 보안 표면 ↑ (compiler 포함).
- **기각 이유**: Epic DoD "최종 이미지에 빌드 산출물 미포함" 위반.

### Option B. Jib (Gradle 플러그인 기반 빌드)

- **Pros**: Dockerfile 불필요. Docker 데몬 없이 빌드. *레이어 자동 분리* (deps/classes/resources).
- **Cons**: Gradle 플러그인 학습 비용. 디버깅 시 Dockerfile 직접 검증보다 불투명. 본 프로젝트는 *Docker 자체 학습* 도 목적 (Product 2 EC2 배포 학습 컨텍스트).
- **기각 이유**: 학습 목적 + 명시성 우선. T7 — *유의미한 빌드 시간/이미지 차이 측정* 후 재평가.

### Option C. Spring Boot `bootBuildImage` (Paketo Buildpacks)

- **Pros**: `./gradlew bootBuildImage` 한 줄. CNB 표준. 자동 JRE 선택 · 자동 레이어링.
- **Cons**: 결과 이미지 base 가 *Paketo bionic* 등 외부 결정 — *학습용으론 블랙박스*. 빌드 환경에 Docker 데몬 필요. Buildpack 캐시 운영 비용.
- **기각 이유**: 블랙박스 + 본 Epic 의 *명시적 멀티스테이지 학습* 목적과 불일치.

### Option D. Distroless (`gcr.io/distroless/java21`)

- **Pros**: 최소 base — shell · package manager · CA 외 모든 것 제거. 보안 표면 최소.
- **Cons**: 컨테이너 안에서 `exec` · 디버깅 (`apt install ...`) 불가. 운영 미숙 단계에서는 *장애 시 진단 불가*.
- **기각 이유**: T7 — 학습 곡선 · 운영 성숙도 도달 후 재평가.

### Option E. Alpine 기반 (`eclipse-temurin:21-jdk-alpine`)

- **Pros**: 더 작은 base (~5MB vs ~70MB).
- **Cons**: `musl libc` — `glibc` 전제 네이티브 라이브러리 (예: 일부 JDBC driver, AWS SDK 네이티브) 호환성 리스크. 본 프로젝트는 MySQL connector + AWS SDK S3 사용.
- **기각 이유**: T7 — *호환성 검증 비용* > *크기 절감 이득* (현 단계).

## Consequences

### Positive

- Epic DoD 4건 충족: 멀티스테이지 / 정상 기동 / 빌드 산출물 미포함 / 크기 감소 측정 가능.
- 빌드 캐시 효율: 소스만 바뀐 재빌드 시 의존성 레이어 재사용 → 로컬 반복 빌드 빠름.
- 보안 표면 축소: runtime 에 compiler · src · gradle 캐시 부재.
- `Dockerfile` 자체가 *결정의 코드 표현* — 신규 합류자가 빌드/실행 의도를 1파일로 파악.
- `eclipse-temurin` base 는 Spring Boot reference + Adoptium 공식 → *재현성 + 신뢰성*.

### Negative / Trade-offs

- Dockerfile 1파일 추가 = 유지보수 책임 1파일. JDK 메이저 업그레이드 시 *2곳 (builder + runtime) 동시 갱신* 필요.
- `eclipse-temurin:21-jdk` / `:21-jre` 가 *Ubuntu* 기반 — Alpine 대비 ~70MB 큼.
- 빌드 시간: 첫 빌드는 의존성 다운로드 1회 + 소스 컴파일 1회 + jar 패키징 → *로컬 JDK 직접 빌드보다 느림*. 캐시 효율로 보상.
- `-x test` 결정: 컨테이너 빌드 단계에서 테스트가 *돌지 않음*. CI 가 잡지 못하면 *깨진 jar* 이미지화 가능 → CI 신뢰도 의존.

### Neutral

- ADR-0008 yml profile 분리 정책은 *컨테이너 안에서도 그대로* — `SPRING_PROFILES_ACTIVE` 환경변수 주입으로 활성화. Dockerfile 은 *profile 비결정* (default 는 local, 운영은 외부 주입).
- ADR-0010 actuator 정책 (`/actuator/health` 만 노출) 그대로 — Docker 가 추가로 노출하지 않음.
- `EXPOSE 8080` 은 *메타데이터* — 실제 포트 매핑은 `docker run -p` 가 결정. server.port 변경 시 본 ADR 갱신.

## Follow-ups

- [ ] `.gitattributes` 에 `gradlew text eol=lf` 추가 — Windows checkout 시 CRLF → 컨테이너 내 `bash\r` 오류 방지.
- [ ] non-root `USER` 추가 (보안 표면) — 별도 PR.
- [ ] `HEALTHCHECK` 지시어 추가 (`curl -f localhost:8080/actuator/health`) — `curl` 미포함 base 라 `wget` 또는 Spring Boot probe 필요.
- [ ] ECR push 자동화 + CI 통합 — 별도 Epic.
- [ ] Distroless / Alpine 재평가 — 운영 성숙도 + 호환성 검증 비용 도달 시.
- [ ] Jib / bootBuildImage 재평가 — Dockerfile 유지보수 부담 vs 빌드 일관성 비교 시.
- [ ] JVM heap 튜닝 (`-Xmx` 등) — t3.micro 1GB 메모리 압박 발생 시.
- [ ] Liveness/Readiness probe 분리 (`probes.enabled: true`) — K8s 도입 시.
- *재검토 트리거*: Spring Boot 메이저 업그레이드 (Java 25+) / 베이스 이미지 보안 인시던트 / 이미지 크기 200MB 초과 / 빌드 시간 5분 초과.
