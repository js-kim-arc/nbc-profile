# Epic 2. 운영성 표준 — 로그 표준 포맷 · ERROR 정책 · Actuator Health 화이트리스트

## Epic 목표

> 모든 API 요청이 `[API - LOG] {METHOD} {URI} {STATUS} ({DURATION}ms)` 포맷의 INFO 로그를 남기고, 예외는 *WARN(예상)/ERROR(예상치 못함)*로 분리되며, `/actuator/health`가 *익명 호출 가능*하고 그 외 endpoint는 *명시적 차단*된 상태를 만든다.
>

## 배경

- 본 Epic은 본 Product의 *고유한 가치* — Profile 분리는 어디서나 같지만 *로그 표준 + Actuator 정책*은 본 Product의 시그니처 결정
- Monitoring Product의 `/actuator/prometheus`가 *본 Epic의 화이트리스트 컨벤션 위에* 추가됨 → *지금* 박지 않으면 향후 *전면 재검토*
- 로그 표준을 *지금* 박지 않으면 *모든 후속 코드*가 *비표준 로그*로 작성됨 → 일관성 회복 비용 큼

## 핵심 설계 결정

> **요청 로그는 `Filter`에서, 예외 로그는 `GlobalExceptionHandler`에서.**
AOP를 *지금은* 도입하지 않음.
>
> - `RequestLoggingFilter` (`nbc.profile.common.web.logging`): 요청 진입/완료를 *Servlet Filter 레벨*에서 잡음 — 컨트롤러 도달 전 예외도 포착
> - 본 프로젝트 `GlobalExceptionHandler` (`nbc.profile.common.exception`): 예외 발생 시 *분류 + 로그 + 응답 변환*. 본 코드는 `BusinessException + ErrorCode(HttpStatus)` 통합 처리 — 로그 레벨은 *status 기반 자동* (ADR-0009)
> - AOP는 *비즈니스 메서드 단위* 로깅이 필요할 때 — 본 Product 범위 밖
> - 단점: Filter 로그가 *Spring Boot 부팅 전 예외*는 못 잡음. 무관 — 부팅 예외는 *systemd 로그*로 잡힘

> **로그 레벨 분리: INFO(요청), WARN(예상 예외), ERROR(예상치 못한 예외).**
>
> - INFO: 모든 요청 1회 — 향후 Monitoring Epic 의 RPS 메트릭과 1:1
> - WARN: `BusinessException` 중 `code.getStatus().is4xxClientError()` (자동) + `MethodArgumentNotValidException` + `MaxUploadSizeExceededException` + `NoHandlerFoundException`
> - ERROR: `BusinessException` 중 `is5xxServerError()` (자동) + catch-all `Exception` (stack trace 포함)
> - 이유: 운영에서 *ERROR = 즉시 페이저*, *WARN = 일간 검토* 의 자동화 전제
> - 향후 Monitoring Epic 의 *에러율 메트릭* 에서 *5xx만 카운트* → ERROR 로그와 자연스럽게 정렬

> **`/actuator/health`의 `show-details: never`.**
익명 호출자는 *UP/DOWN만* 응답.
>
> - `when-authorized`는 *Spring Security가 구성된 후*에 의미 — 본 Product에는 인증 없음
> - `never`는 *모든 호출자에게 단순 응답* — `{"status":"UP"}` 한 줄
> - 이유: 익명 호출에 *DB 상태·디스크 정보 노출* 시 *내부 구조 fingerprinting* 가능
> - 향후 ALB Health Check도 *200 OK만 필요*하므로 details 불필요
> - 향후 Monitoring Epic 에서 `when-authorized` 도입 검토 — *지금은 보수적*
> - ADR-0010 의 코드 표현

> **Actuator `exposure.include`는 *명시 화이트리스트*, `exposure.exclude`는 *방어 블랙리스트*.**
이중 안전망.
>
> - `include: health` — 명시적으로 *health만* 허용
> - `exclude: env, beans, heapdump, threaddump, configprops, mappings` — 향후 실수 노출 방지
> - 단점: 중복 정의처럼 보임. 무관 — *명시성*이 *최선의 방어*

## 완료 기준 (Definition of Done)

- [ ]  `RequestLoggingFilter`가 모든 API 요청 1회당 *진입 + 완료* 2건 로그 출력
- [ ]  로그 포맷이 `[API - LOG] {METHOD} {URI} {STATUS} ({DURATION}ms)` 표준 준수
- [ ]  `GlobalExceptionHandler`가 *status 기반 자동* WARN/ERROR 분리 (ADR-0009)
- [ ]  ERROR 로그에 *stack trace* 자동 포함 (`log.error("...", e)`)
- [ ]  `GET /actuator/health` 200 OK + `{"status":"UP"}` 응답
- [ ]  `GET /actuator/env` 404 (RESOURCE_NOT_FOUND)
- [ ]  `GET /actuator/heapdump` 404
- [ ]  `GET /actuator/beans` 404
- [ ]  로컬 profile에서 *DEBUG* 로그가 추가로 출력 (`logging.level.nbc.profile: DEBUG`, Story-001-1 적용분)
- [ ]  prod profile에서 *INFO 이상*만 출력
- [ ]  연결된 Story가 모두 Done 상태다

## 산출물 (Deliverables)

- `nbc/profile/common/web/logging/RequestLoggingFilter.java`
- `nbc/profile/common/exception/GlobalExceptionHandler.java` 업데이트 (status 기반 WARN/ERROR + NoHandlerFound 404 매핑)
- `nbc/profile/common/exception/ErrorCode.java` — `RESOURCE_NOT_FOUND` 추가
- `application.yml` 공통에 `management.endpoints.*` + `endpoint.health.show-details: never`
- `build.gradle.kts`에 `spring-boot-starter-actuator` 추가
- `nbc/profile/common/web/logging/RequestLoggingFilterTest.java`
- `nbc/profile/common/web/ActuatorEndpointTest.java`
- ADR-0009 / ADR-0010

## 연결된 Story 목록

- [ ]  Story 2-1. 로그 표준 포맷 + ExceptionHandler 로그 + Actuator Health 화이트리스트 (3 SP)

## 내부 메모 / 제약 사항

- `RequestLoggingFilter`는 *Actuator · h2-console 경로 제외* (`shouldNotFilter`) — 향후 Monitoring Epic 의 prometheus 스크랩이 로그 노이즈 되지 않도록 *지금* 박음
- Logback 설정은 *Spring Boot 기본*을 사용 — `logback-spring.xml` 커스터마이징은 본 PR 범위 외 (필요시 후속 PR)
- 표준 포맷에 *시간 / 스레드 / 로거명*은 Logback 기본 패턴이 처리 — 본 PR 책임은 *메시지 본문* 표준화만
- 향후 Monitoring Epic 의 *traceId / MDC*는 *이 표준 포맷에 자연 추가* 됨 — 본 Product는 *비워 둠*
- 본 Epic 진입 시 ADR-0009 (로그 표준) + ADR-0010 (Actuator 노출 정책) 작성
- `BusinessException + ErrorCode(HttpStatus)` 통합 처리 구조 활용 — status 기반 자동 WARN/ERROR 분기 (새 예외/ErrorCode 추가 시 핸들러 무수정)