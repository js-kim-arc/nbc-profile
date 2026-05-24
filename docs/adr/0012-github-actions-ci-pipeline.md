# ADR-0012: GitHub Actions CI 파이프라인 + Gradle 캐시 + Artifact 업로드

- **Status**: Proposed
- **Date**: 2026-05-24
- **Deciders**: junseong kim
- **Trigger**: T4 (CI 도구 도입), T1 (GitHub Actions vs Jenkins/CircleCI/GitLab CI), T3 (워크플로 컨벤션 신규), T7 (Jacoco · Spotless · SpotBugs · Checkstyle 연기)

## Context

Epic 1-1 으로 *런타임 패키징* (멀티스테이지 Docker) 까지 완료. *수동 빌드/테스트* 의 결함:
- 머지 직전 `./gradlew test` 잊고 진행 → 깨진 코드가 main 진입 → Epic 1-3 의 CD 가 깨진 jar 를 배포.
- PR 리뷰어가 *로컬에서 checkout 하고 돌려야* 빌드 상태 확인 가능 → 리뷰 사이클 길어짐.
- 다음 Epic 1-3 (Docker Hub Push + EC2 Pull) 의 *trigger 가 되는 안정 신호* 필요.

본 프로젝트 컨텍스트:
- 저장소: GitHub (`js-kim-arc/nbc-profile`)
- 빌드: Gradle Wrapper 9.4.1 + Java 21
- 테스트: JUnit 5 (11개 테스트 파일, Filter/Actuator/Smoke 등)
- 부 ProductMonitoring 도입 시 메트릭 신호와 *CI 결과 신호* 가 함께 작용 — 일찍 표준화 필요.

## Decision

**GitHub Actions + `actions/setup-java@v4` + `gradle/actions/setup-gradle@v4` (네이티브 캐시) + `actions/upload-artifact@v4` (테스트 리포트 + JAR)** 채택:

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew clean build
      - if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: build/reports/tests
          retention-days: 7
      - if: success()
        uses: actions/upload-artifact@v4
        with:
          name: app-jar
          path: build/libs/*-SNAPSHOT.jar
          retention-days: 7
```

핵심 규칙:
- **runner = `ubuntu-latest`**: macOS/windows 대비 ~10배 저렴 + 운영 환경(AL2023) 과 OS 계열 일치.
- **`setup-java@v4`** distribution=temurin / java-version=21: Epic 1-1 Dockerfile 의 base 와 동일 vendor + major 버전 → *CI 패스 == 컨테이너 패스* 보장.
- **`gradle/actions/setup-gradle@v4`** (구 `gradle-build-action` 후속): GitHub-hosted runner cache 를 *Gradle 전용 키* 로 자동 관리 → 첫 빌드 후 deps/wrapper/configuration-cache 재사용.
- **`./gradlew clean build`**: `build` = compile + test + jar. `clean` 으로 incremental 잔여 영향 제거 → CI 재현성.
- **테스트 리포트 `if: always()`**: 테스트 실패 시에도 리포트 업로드 → Actions UI 에서 실패 원인 확인.
- **JAR `if: success()`**: 빌드 성공 시만 업로드 → Epic 1-3 의 Docker Push 후보 산출물.
- **paths 필터 미적용 (`docs/` 변경도 실행)**: Story 1-2-1 AC 엣지 케이스 명시 — 단순화 우선. v2 에서 paths-ignore 검토.

## Alternatives Considered

### Option A. Jenkins (self-hosted)

- **Pros**: 무료 · 플러그인 풍부 · 운영 완전 통제.
- **Cons**: EC2 t3.micro 위에 띄우면 메모리 압박. 별도 운영 비용 (업그레이드 · 보안 패치). 학습/관리 곡선.
- **기각 이유**: 학습 컨텍스트에서 *인프라 운영 부담 0* 인 GitHub Actions 가 우월. GitHub 저장소와의 *통합 표면* (PR 체크 / Secrets / Artifacts) 도 GH 가 유리.

### Option B. CircleCI / Travis / GitLab CI

- **Pros**: 각 도구별 강점 (CircleCI orbs, GitLab CI builtin).
- **Cons**: 저장소가 GitHub → 별도 vendor 연동. 무료 한도 변동.
- **기각 이유**: 저장소 호스트(GH) 와 CI 호스트 일치 시 *PR 체크 자동 통합* + Secrets/Artifacts 단일 콘솔.

### Option C. Gradle 캐시 — `actions/cache@v4` 수동 키 지정

- **Pros**: 캐시 키 완전 제어.
- **Cons**: `~/.gradle/caches`, `~/.gradle/wrapper`, `build` 등 key 설계 오류 시 *캐시 무효화 폭증* 또는 *오염*.
- **기각 이유**: `gradle/actions/setup-gradle` 가 *Gradle 도구 팀 권장* 의 캐시 키 자동 관리. 학습/유지 비용 0.

### Option D. paths-ignore 로 docs 변경 시 CI 스킵

- **Pros**: 무용 빌드 시간 절약.
- **Cons**: README 만 바꿔도 *체크 상태 부재* → 머지 게이트 모호. 학습 단계에 *복잡도 증가*.
- **기각 이유**: T7. v2 에서 빌드 시간이 부담 될 때 도입.

### Option E. Jacoco / Spotless / SpotBugs / Checkstyle 통합

- **Pros**: 커버리지 + 코드 품질 자동.
- **Cons**: Gradle plugin 추가 + 룰셋 수립 + 위반 임계 결정 = 별도 ADR.
- **기각 이유**: T7. 본 Epic 의 *빌드/테스트 게이트* 책임에 집중. 별도 Epic.

## Consequences

### Positive

- PR 시점에 *빌드/테스트 그린* 자동 확인 → 머지 게이트.
- Epic 1-3 (CD) 의 *상위 의존 신호* 확보 (workflow_run trigger 등).
- Artifact 업로드 → Epic 1-3 의 Docker Push 단계가 *이 JAR* 또는 *Gradle 재빌드* 선택 가능.
- 테스트 리포트 *실패 시 다운로드* → 로컬 재현 부담 ↓.
- `setup-gradle` 캐시 → 2회차 이후 ~30~50% 단축 (실측은 Story 1-2-2 검증에서 보강).

### Negative / Trade-offs

- GitHub Actions 무료 한도: public repo 무제한 / private 2,000분/월 (Free) — 본 repo public 이라 무관.
- CI 결과가 *틀린 환경* (Linux x86_64) 에서 도출 — 사용자 로컬 (Windows) 와 차이는 *컨테이너 일관성* (Epic 1-1) 으로 흡수.
- `clean build` = 매 실행 incremental 미활용 → 정확성 우선, 시간 비용 감수.
- `actions/*@v4` 메이저 핀: 4.x 마이너 업데이트는 자동 반영 (보안 패치 자동 수용). 5.x 진입 시 ADR 갱신.

### Neutral

- Dockerfile 빌드는 본 Epic 범위 밖 (Epic 1-3 책임). 본 CI 는 *Gradle JAR 생성 + 테스트* 만.
- artifact retention 7일: GitHub 무료 한도 (90일 가능하나 저장소 부담 회피).
- PR 체크 등록은 *workflow 자동 생성* — 별도 branch protection 설정은 사용자 GitHub 측 작업.

## Follow-ups

- [ ] Jacoco 커버리지 + PR 코멘트 (codecov / `madrapps/jacoco-report`) — 별도 Epic.
- [ ] Spotless · SpotBugs · Checkstyle — 별도 ADR.
- [ ] paths-ignore 도입 (빌드 시간 부담 시).
- [ ] `workflow_run` 트리거로 Epic 1-3 의 CD workflow 연계.
- [ ] GitHub Branch Protection 규칙 (필수 CI 통과 + 1 review) — *사용자 GitHub UI 작업*.
- [ ] matrix build (Java 21 + 25 동시) — 향후 LTS 업그레이드 시.
- *재검토 트리거*: 빌드 시간 10분 초과 / 캐시 적중률 저하 / GitHub Actions 가격 정책 변경 / Spring Boot 메이저 업그레이드.
