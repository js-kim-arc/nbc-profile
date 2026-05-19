# ADR-0008: application-{profile}.yml 분리 채택 (ADR-0001 Supersede)

- **Status**: Accepted
- **Date**: 2026-05-19
- **Deciders**: junseong kim

## Context

ADR-0001 (2026-05-18 Accepted) 은 *환경변수 기반 단일 yml* 채택. 그러나 Epic 1 "환경별 Profile 분리" 진입 시점 (2026-05-19) 에 다음 force 가 드러남:

- *환경 차이가 값 수준이 아닌 구조 수준* — 예: prod 만 `driver-class-name: com.mysql.cj.jdbc.Driver`, local 만 `spring.h2.console.enabled: true`. placeholder 로 표현 시 *기본 driver=H2 + `${VAR:org.h2.Driver}`* 같은 *의미 모호* 디폴트 필요.
- *로그 레벨 환경별 차이* — local `nbc.profile: DEBUG` / prod `INFO` 같은 정책 차이를 placeholder 로 표현하면 yml 가독성 손상.
- *부팅 시점 fail-fast* — prod 에서 `${DB_URL}` 디폴트 없이 두면 *환경변수 미설정 시 부팅 실패* (Epic 1 핵심 안전망). 같은 placeholder 를 local 에선 *디폴트 있게* 두려면 *환경별 다른 placeholder* 가 필요 → 사실상 *파일 분리* 와 동등 복잡도.
- *IDE 친화* — IntelliJ profile 드롭다운으로 활성 profile 가시 전환.

12-factor *Config-in-environment* 원칙은 *시크릿은 환경변수* 만 지킨다면 *구조 차이는 파일로* 표현해도 충돌하지 않는다 — 시크릿과 *구조 설정* 의 분리.

## Decision

`application-{profile}.yml` 분리 채택. 본 프로젝트는 다음 3개 파일을 둔다:

- `application.yml` — *공통* 설정 (`spring.application.name`, JPA `format_sql` · `time_zone`, multipart, Jackson, S3 공통 placeholder) + `spring.profiles.default: local`.
- `application-local.yml` — H2 DataSource, h2-console, `show-sql: true`, `nbc.profile: DEBUG`.
- `application-prod.yml` — MySQL `${DB_URL}` *strict* (디폴트 제거), S3 *strict* override, `show-sql: false`, `nbc.profile: INFO` / Hibernate SQL `WARN`, `ddl-auto: validate` override.

**ADR-0001 Supersede**. ADR-0001 의 Follow-ups 중 *"application-{profile}.yml 신규 생성 금지"* 항목은 본 ADR 로 *철회*.

**시크릿은 여전히 환경변수만**. 분리된 yml 파일에 `DB_PASSWORD` · S3 access key 등 시크릿을 *직접 기재 금지* — 모두 `${VAR}` placeholder. yml 분리는 *구조 차이* 표현 수단이지 *시크릿 분기 수단이 아님*.

`@Profile` 어댑터 분기 (`S3FileStorageAdapter @Profile("!test")`, `InMemoryFileStorageAdapter @Profile("test")`) 는 *별개 정책* — 본 ADR 미적용.

## Alternatives Considered

### Option A: ADR-0001 유지 (단일 yml + env placeholder)

- **Pros**:
  - 12-factor 정합 (Config-in-environment) — 시크릿 파일 누설 위험 0.
  - 새 환경 추가 = 환경변수만 변경 (파일 변경 0).
- **Cons**:
  - 환경별 *구조 차이* (driver-class-name, h2-console.enabled, logging.level) 를 *디폴트 박힌 placeholder* 로 표현 → yml 가독성 손상.
  - prod 의 *strict fail-fast* (`${VAR}` 디폴트 없음) 와 local 의 *디폴트 있는 placeholder* 가 *동일 키* 에서 충돌 → placeholder 두 개 또는 별도 처리 필요.
- **기각 이유**: 환경별 *구조 차이* 표현 한계 + Epic 1 의 *placeholder fail-fast 안전망* 과 *local 디폴트* 양립 어려움.

### Option B: profile 별 yml 분리 (채택)

- **Pros**:
  - 환경 차이가 *파일 단위* 가시화 — 리뷰·디버깅 용이.
  - prod 만 *strict placeholder*, local 은 *디폴트 박힘* 으로 자연 분리.
  - IDE profile 드롭다운 활용.
  - Spring Boot 기본 메커니즘 — 추가 도구 0.
- **Cons**:
  - 새 환경 추가 시 새 파일 (운영 환경이 5개 이상으로 늘면 번잡).
  - *시크릿 파일 누설* 가능성 → *컨벤션 강제* (yml 에 시크릿 직접 기재 금지) 로 방어.
- **기각 사유**: 없음 — 채택.

### Option C: Spring Boot 4 multi-document yaml (`spring.config.activate.on-profile`)

- **Pros**: 파일 한 장 유지 + profile 분기.
- **Cons**: 한 파일 안에 다수 document → 가독성 손상. IDE 친화도 낮음.
- **기각 이유**: 분리의 *가시성* 이점이 다중 document 로는 충분히 살아나지 않음.

## Consequences

### Positive

- 환경별 구조 차이 *파일 단위* 가시화.
- prod 환경변수 *fail-fast* 안전망 단순화 (strict placeholder 만).
- local 개발 진입 비용 낮음 (디폴트 박힘).
- IDE / `--spring.profiles.active=...` / `SPRING_PROFILES_ACTIVE=...` 모두 자연 지원.

### Negative / Trade-offs

- 새 환경 추가 시 *새 파일* 추가 (Option A 는 환경변수만).
- *시크릿 파일 누설* 위험 → 컨벤션 강제로 방어 (yml 에 시크릿 직접 기재 금지).
- `ddl-auto` 같은 *공통 vs 환경별* 결정이 늘어남 — *공통 `update`, prod 만 `validate` override* 같은 정책 명시 필요.

### Neutral

- `@Profile` 어댑터 분기 (`S3FileStorageAdapter @Profile("!test")`, `InMemoryFileStorageAdapter @Profile("test")`) 와 *공존*. 어댑터 분기는 *별개 정책*.
- ADR-0002 (validation starter), ADR-0003 (`@EnableConfigurationProperties`), ADR-0004 (S3 credentials nullable) 는 본 결정과 *직교* — 영향 없음.
- S3 설정은 *공통 application.yml* 에 디폴트 placeholder 유지 (`${S3_BUCKET:my-toy-bucket}` 등). local/test 컨텍스트 로딩 시 `S3StorageProperties @NotBlank bucket` 검증 만족용. prod 는 `application-prod.yml` 에서 *strict* override (디폴트 제거).

## Follow-ups

- [ ] `application.yml` 을 *공통* 만으로 재작성.
- [ ] `application-local.yml`, `application-prod.yml` 신규 작성.
- [ ] `ProfileSmokeTest` — local + test profile 컨텍스트 로딩 검증.
- [ ] README 에 *profile별 실행 명령* + *환경변수 셋업* 가이드 추가.
- [x] ADR-0001 status 를 `Superseded by ADR-0008` 로 변경.
- [ ] *시크릿 컨벤션 보강* — `.claude/rules/conventions.md` §3 또는 §유사 절에 "profile yml 에 시크릿 직접 기재 금지, `${VAR}` placeholder 만" 1줄 추가 (별도 PR 가능).
- [ ] *재검토 트리거*: 환경 수가 5개 이상으로 늘거나, *시크릿 누설 사고* 발생 시 본 결정 재평가.
