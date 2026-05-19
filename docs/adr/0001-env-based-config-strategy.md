# ADR-0001: 환경변수 기반 설정 분기 채택, profile 별 yml 파일 분리 회피

- **Status**: Superseded by [ADR-0008](0008-profile-based-yml-split.md)
- **Date**: 2026-05-18
- **Superseded-on**: 2026-05-19
- **Deciders**: junseong kim

## Context

`nbc-profile/demo` 는 *클라우드 주차 과제* 로 로컬 개발 (LocalStack 가능) · CI · 운영 (AWS) 환경 모두에서 동작해야 한다. S3 버킷 이름 · 리전 · 엔드포인트 · 자격증명이 환경마다 다르다.

Spring Boot 가 *둘 다 지원* 하는 두 전략 사이의 선택이 필요:
- `application-{profile}.yml` 파일 분리 + `spring.profiles.active` 로 분기
- 단일 `application.yml` + `${VAR:default}` placeholder + 환경변수 주입

본 결정 이전까지 본 프로젝트는 *어느 쪽도 명시 채택하지 않은 채* `@Profile("!test")` / `@Profile("test")` 어댑터 분기와 application.yml 의 *일부 placeholder* 가 혼재했다.

## Decision

**환경변수 기반 분기 (env-based) 채택**. `application.yml` 한 장 + `${VAR:default}` placeholder + 환경변수로만 환경 차이 표현. `application-{profile}.yml` 분리 파일은 만들지 않는다.

본 결정은 `@ConfigurationProperties` (설정 데이터) 의 등록 시점 분기에 적용된다. *런타임 어댑터 분기* (`S3FileStorageAdapter @Profile("!test")` 같은 Spring `@Profile` 어노테이션) 는 *별개 결정* 이며 본 ADR 의 범위 밖이다 — 테스트 격리 목적의 어댑터 교체는 유지.

## Alternatives Considered

### Option A: `application-{profile}.yml` 분리

- **Pros**: Spring 기본 지원·IDE 친화·환경별 다른 값을 *한 파일 단위로* 읽기 쉬움.
- **Cons**:
  - 환경별 시크릿이 *파일* 에 박힐 위험 (또는 별도 `.gitignore` 정책 필요).
  - CI/CD 파이프라인이 *어떤 profile 을 띄울지* 추가 결정.
  - 새 환경 추가 시 새 파일.
- **기각 이유**: 12-factor app *Config-in-environment* 원칙 위배. 시크릿 외부화에 추가 장치 필요.

### Option B: env-based + `${VAR:default}` (채택)

- **Pros**:
  - 12-factor 정합.
  - 시크릿은 환경변수로만 — 파일 누설 위험 0.
  - CI/CD 가 환경변수만 주입하면 됨.
  - 새 환경은 새 *환경변수 값* 만 — 파일 변경 0.
- **Cons**:
  - 로컬 개발 시 환경변수 셋업 부담.
  - `${VAR:}` 빈 값 의미 (null vs empty) 가 hibernate-validator 동작에 영향 — `@URL` 등이 빈 문자열을 valid 로 봄에 의존.
- **기각 사유**: 없음 — 채택.

## Consequences

### Positive

- 12-factor 정합.
- 시크릿 외부화가 *기본 동작* — 별도 정책 불필요.
- CI/CD 환경변수 주입 단순.
- 환경 추가 시 *코드 / 파일 변경 0*.

### Negative / Trade-offs

- 로컬 개발 시 `S3_BUCKET` 등 환경변수 셋업 필요. → `.env` 파일 + IDE Run Configuration · `direnv` · `dotenv-cli` 로 해소.
- *환경변수 미설정* 이 `${VAR:}` 빈 값으로 해석되어 `@NotBlank` 검증에서 *부팅 실패* — 의도된 fail-fast 이나 *원인 메시지* 가 환경변수 누락이라는 점이 즉시 보이지 않을 수 있음.

### Neutral

- `S3StorageConfig` 와 `S3FileStorageAdapter` 의 `@Profile` 어댑터 분기는 *별개 정책* — 본 ADR 미적용.
- 향후 다른 외부 서비스 (Redis · 메시지 큐 등) 도입 시 동일 패턴 자동 상속.

## Follow-ups

> 본 ADR 은 ADR-0008 로 Superseded. 아래 Follow-ups 는 *Superseded 시점 (2026-05-19) 기준 상태* 로 동결.

- [ ] ~~`application.yml` 의 모든 환경 의존 값을 `${VAR:default}` 형태로 통일.~~ → ADR-0008 에서 *공통 yml 의 디폴트 placeholder + prod yml 의 strict placeholder* 로 정책 재정의.
- [ ] ~~`application-{profile}.yml` 신규 생성 금지~~ → ADR-0008 로 *철회*. profile yml 분리 채택.
- [ ] 로컬 개발 가이드 (`.env` / 환경변수 셋업) README 추가 — *ADR-0008 의 Follow-up* 으로 이관.
- [x] *재검토 트리거 발화* — 2026-05-19 Epic 1 진입 시 환경별 *yml 구조 자체* 차이 (driver-class-name, h2-console, logging level) 가 placeholder 로 표현하기 어려움이 확인되어 ADR-0008 로 재평가.
