# ADR-0003: `@EnableConfigurationProperties` 명시 등록 채택, `@ConfigurationPropertiesScan` 회피

- **Status**: Accepted
- **Date**: 2026-05-18
- **Deciders**: junseong kim

## Context

`@ConfigurationProperties` 가 붙은 record 를 Spring 컨테이너에 *빈으로 등록* 하려면 (1) `@ConfigurationPropertiesScan` 자동 스캔, 또는 (2) `@EnableConfigurationProperties(...)` 명시 등록 두 가지 방식이 있다.

본 결정 이전까지 본 프로젝트의 `S3StorageConfig` 가 *명시 등록* 을 사용하고 있었으나 *명문화된 룰이 없었다* — 새 Properties 추가 시 어느 쪽을 쓸지 매번 즉흥 결정.

## Decision

**`@EnableConfigurationProperties(XxxProperties.class)` 를 *그 Properties 를 실제로 쓰는 Config 클래스* 에 명시 부착** 한다. `@ConfigurationPropertiesScan` 은 사용하지 않는다.

원칙: *Properties 와 Properties 를 소비하는 Bean 은 같은 파일* 에 둔다 — 응집도.

## Alternatives Considered

### Option A: `@ConfigurationPropertiesScan` 자동 감지

- **Pros**: 보일러플레이트 0. 새 Properties 추가 시 등록 코드 작성 불필요.
- **Cons**:
  - *어디서 등록되는지* 코드만 봐서는 모름 — 패키지 의존.
  - *부분 컨텍스트 테스트* (Slice / `@SpringBootTest(classes = ...)`) 에서 *Properties 일부만 로드* 하기 어려움.
  - 의존성 추적이 *암묵적* — IDE Find Usages 가 어노테이션 등록을 못 봄.
- **기각 이유**: 명시성·테스트 격리·추적성 모두에서 손해.

### Option B: `@EnableConfigurationProperties` 명시 + 같은 Config 에 소비 Bean (채택)

- **Pros**:
  - *어디서 등록되는지* 명확.
  - Properties + 그것을 쓰는 Bean 이 *같은 파일* — 응집도.
  - 테스트에서 *해당 Config 만 Import* 하면 Properties 도 같이 따라옴 — 격리 친화.
  - IDE Find Usages 가 어노테이션 인자를 따라감.
- **Cons**: 새 Properties 마다 등록 어노테이션 한 줄 필요.
- **기각 사유**: 없음 — 채택.

### Option C: `@Component` 부착 (record 에 직접)

- **Pros**: 가장 짧음.
- **Cons**: Spring Boot 권장 패턴 아님. record + `@ConfigurationProperties` 와 모순.
- **기각 이유**: 표준 외 패턴.

## Consequences

### Positive

- *어디서 등록되는지* 코드만 봐도 알 수 있음.
- Properties · 그것을 쓰는 Bean 응집.
- 테스트 격리 친화 (`@Import(S3StorageConfig.class)` 하나로 Properties 까지 로드).

### Negative / Trade-offs

- 새 Properties 마다 `@EnableConfigurationProperties` 인자 추가 — 한 줄 보일러플레이트.
- *여러 Config 가 같은 Properties 를 공유* 하는 케이스에서 *중복 등록* 위험 (Spring 은 무시하지만 가독성 손상).

### Neutral

- 향후 Properties 가 *Config 1:1* 이 아닌 *공유* 구조가 되면 본 결정 재평가.

## Follow-ups

- [ ] `S3StorageConfig` 에 `@EnableConfigurationProperties(S3StorageProperties.class)` 부착.
- [ ] `.claude/rules/conventions.md` 에 본 패턴 한 줄 보강 — *"새 `@ConfigurationProperties` 는 소비 Config 에 `@EnableConfigurationProperties` 명시 등록. `@ConfigurationPropertiesScan` 사용 금지."* (별도 PR).
- [ ] *재검토 트리거*: Properties 가 *여러 Config 에서 공유* 되어야 하는 케이스 발견 시 본 결정 재평가.
