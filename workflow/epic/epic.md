# Epic 1. 환경별 Profile 분리 — `application-{profile}.yml` · `@Profile` 일관 적용

## Epic 목표

> `application.yml`(공통) · `application-local.yml`(H2) · `application-prod.yml`(MySQL placeholder) 3개 파일로 환경 설정이 분리되고, *active profile 설정만으로* 환경이 즉시 전환되는 상태를 만든다.
본 프로젝트에 이미 적용된 `@Profile` 기반 어댑터 빈 분리(`S3FileStorageAdapter @Profile("!test")` · `InMemoryFileStorageAdapter @Profile("test")`)와 *일관된 컨벤션*을 유지한다.
>

## 배경

- 본 Product의 *최우선 토대* — 로그·Actuator·배포 모두 *환경별로* 다르게 동작해야 함
- 본 프로젝트가 이미 *어댑터 빈 분리*에 `@Profile` 사용 중 (`S3FileStorageAdapter @Profile("!test")` / `InMemoryFileStorageAdapter @Profile("test")`) → 이 Epic은 *설정 값 분리*까지 확장
- Profile 결정이 *Bean Definition 시점*에 일어남 → 런타임 변경 불가, 시작 시 결정만
- ADR-0001 (env-based 단일 yml) 은 본 Epic 진입 시 ADR-0008 로 Supersede — *환경 구조 차이* 표현 한계로 *profile yml 분리* 채택

## 핵심 설계 결정

> **`application.yml`은 *공통 설정만*. 환경별 값은 *profile 파일에서 override*.**
>
> - 공통: JPA `format_sql`/`time_zone`, multipart 5MB, Jackson, `app.storage.s3.*` 디폴트 placeholder, `spring.profiles.default: local` 등 *환경 무관* 항목
> - `application-local.yml`: H2 in-memory, h2-console, `show-sql: true`, 로그 `nbc.profile=DEBUG`
> - `application-prod.yml`: MySQL placeholder *strict* (`${DB_URL}` 디폴트 제거), S3 bucket/region strict, `ddl-auto: validate` override, 로그 `nbc.profile=INFO`
> - 이유: 공통 / 분기 *경계가 명확*해야 *오버라이드 사고* 방지

> **`@Profile`은 *어댑터 빈 분리* 전용.**
설정 값은 *파일 분리*, 구현체는 *빈 분리* — 역할이 다름.
>
> - 어댑터 빈: `@Profile("!test")` S3FileStorageAdapter (local/default/prod 모두), `@Profile("test")` InMemoryFileStorageAdapter (테스트 격리)
> - 설정 값: `spring.datasource.url`, `logging.level.*` 등은 *yaml 파일*에서
> - 단점: 두 메커니즘 *공존*. 무관 — *역할 분리*가 인지 부담을 *오히려 감소*시킴
> - ADR-0008의 코드 표현 (ADR-0001 Supersede)

> **`default` profile은 *local과 동등*하게 동작한다.**
신규 개발자가 *프로필 지정 없이* 실행해도 H2로 안전하게 뜸.
>
> - `application.yml` 의 `spring.profiles.default: local` 한 줄 — profile 미지정 시 local 로 fallback
> - `application-default.yml`을 *별도로 만들지 않음* — Spring 의 default-profile 메커니즘에 의존
> - 단점: `SPRING_PROFILES_ACTIVE=prod` *명시 안 하면* 운영에서 H2로 뜰 위험 — systemd 서비스 파일에 *반드시 명시* (배포 Epic 에서 처리)

## 완료 기준 (Definition of Done)

- [ ]  `src/main/resources/application.yml`이 *공통 설정만* 담는다
- [ ]  `src/main/resources/application-local.yml`이 H2 DataSource + 로컬 storage 설정
- [ ]  `src/main/resources/application-prod.yml`이 MySQL placeholder + prod 로그 레벨
- [ ]  `./gradlew bootRun` (default) → H2 자동 활성, 기존 4개 Member API 동작
- [ ]  `./gradlew bootRun --args='--spring.profiles.active=local'` → 동일 동작
- [ ]  `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun` → MySQL 연결 시도 (값 미설정 → 부팅 실패 메시지 명확)
- [ ]  본 프로젝트 어댑터 빈이 *각 profile에서 정상 주입*되는지 확인 (local 에서 S3FileStorageAdapter, test 에서 InMemoryFileStorageAdapter)
- [ ]  연결된 Story가 모두 Done 상태다

## 산출물 (Deliverables)

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-prod.yml`
- `ProfileSmokeTest.java` (각 profile에서 ApplicationContext 정상 로딩 검증)

## 연결된 Story 목록

- [ ]  Story 1-1. `application-{profile}.yml` 3분할 + Profile별 컨텍스트 로딩 검증 (2 SP)

## 내부 메모 / 제약 사항

- Spring Boot의 profile 우선순위: *명시 active profile > application-{active-profile}.yml > application-{default-profile}.yml > application.yml*
- `application-prod.yml`의 `${DB_URL}` 같은 *strict* placeholder는 *환경변수 미설정 시* 부팅 실패 — Epic 1 핵심 안전망
- 향후 *AWS Parameter Store / Secrets Manager* 가 placeholder를 채울 수 있음 — 이 Epic은 *placeholder 자리*만 정의
- 로컬에서 *prod profile 실수 활성화* 방지: README에 *prod는 운영 환경에서만* 명시
- 본 Epic 진입 시 ADR-0008 작성 — `application-{profile}.yml` 분리 채택 (ADR-0001 Supersede)
- 시크릿 컨벤션: profile yml 에 시크릿 직접 기재 금지, 모두 `${VAR}` placeholder (ADR-0008 Follow-up)

---