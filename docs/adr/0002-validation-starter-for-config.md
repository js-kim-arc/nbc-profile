# ADR-0002: spring-boot-starter-validation 으로 ConfigurationProperties 의 startup-time 검증

- **Status**: Accepted
- **Date**: 2026-05-18
- **Deciders**: junseong kim

## Context

`@ConfigurationProperties` 로 외부 설정을 받을 때 *필수값 누락* · *범위 위반* · *URL 형식 오류* 같은 입력 오류가 *런타임 호출 시점* 에 발견되면 디버깅 비용이 크다. 부팅 시점에 fail-fast 되어야 한다.

본 결정 이전까지 본 프로젝트의 `S3StorageProperties` 는 *어떤 검증 어노테이션도 없는 record* 였고, 어댑터 (`S3FileStorageAdapter`) 가 `isBlank()` 가드로 *런타임* 에 분기 처리하고 있었다.

## Decision

**`spring-boot-starter-validation` (jakarta.validation + hibernate-validator) 를 도입** 하여 `@Validated` + jakarta `@NotBlank` · `@Min` · `@Max`, hibernate `@URL` 어노테이션으로 *부팅 시점* 검증한다. record 위에 `@Validated` 를 부착하면 Spring 이 startup 시 한 번 검증 — properties 는 startup-only 라 이걸로 충분.

본 starter 는 본 프로젝트 `build.gradle.kts` 에 *이미 포함* (`spring-boot-starter-validation`) 되어 있어 의존성 추가 작업 없음.

## Alternatives Considered

### Option A: 자체 검증 (어댑터 내부 `isBlank()` 가드)

- **Pros**: 외부 의존성 0. 단순.
- **Cons**:
  - 검증 *누락* 위험 (메서드마다 가드 반복 → 한 곳만 빠지면 우회).
  - boilerplate.
  - startup 시점 검증 불가 — *처음 호출* 까지 오류 감지 지연.
- **기각 이유**: fail-fast 부재. 운영 환경에서 *잘못 설정한 채로 부팅* 후 첫 요청에서 발견.

### Option B: hibernate-validator (채택)

- **Pros**: Spring 표준 (Request DTO 검증과 *동일 starter* 공유). 선언적 어노테이션. startup-time 자동 검증.
- **Cons**: jakarta.validation + hibernate-validator + jboss-logging transitive 의존성 증가.
- **기각 사유**: 없음 — 채택.

### Option C: 외부 정책 엔진 (OPA / Cerbos 등)

- **Pros**: 정책을 코드 밖으로.
- **Cons**: 토이 프로젝트에 과한 추상화. 인프라 의존.
- **기각 이유**: 범위 초과.

## Consequences

### Positive

- 필수값 (`bucket`, `region`) 누락 → 부팅 실패. *잘못 띄운 상태로 운영* 방지.
- 표준 어노테이션 (`@NotBlank`, `@Min`, `@Max`, `@URL`) — 학습 비용 0.
- Request DTO Bean Validation 과 *동일 starter* — 재활용.

### Negative / Trade-offs

- jakarta.validation + hibernate-validator + jboss-logging transitive 의존성 (jar 크기 ~5MB).
- hibernate `@URL` 이 *빈 문자열도 valid* 로 봄. 빈 endpoint 를 허용해야 하는 본 프로젝트엔 *오히려 적합* 하지만, 다른 케이스에선 `@NotBlank` 와 함께 써야 함.
- record + `@Validated` 가 *startup-only* 라 *runtime mutation* 시 재검증 안 됨 — properties 는 immutable 이라 무관, 다만 *다른 record 영역* 에서 동일 패턴 사용 시 인지 필요.

### Neutral

- 동일 starter 를 향후 *도메인 / 응용 검증* 에 재활용 가능 (`@Valid` 활용).
- 본 starter 는 이미 `build.gradle.kts` 에 들어있음 — 추가 작업 없음.

## Follow-ups

- [ ] `S3StorageProperties` record 에 `@Validated` + 어노테이션 적용.
- [ ] `.claude/rules/conventions.md` §5 *코드 작성 컨벤션* 에 *config-level validation* 한 줄 보강 (별도 PR).
- [ ] *재검토 트리거*: 검증 어노테이션 적용 케이스가 *config 영역 외* (예: 도메인 객체) 로 확장될 때 *Bean Validation vs 도메인 자체 검증* 정책 재정의.
