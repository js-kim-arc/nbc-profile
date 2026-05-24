# ADR-0009: API 로그 표준 포맷 + status 기반 WARN/ERROR 분리

- **Status**: Accepted
- **Date**: 2026-05-19
- **Deciders**: junseong kim
- **Trigger**: T3 (컨벤션 정의), T5 (운영 트레이드오프)

## Context

운영 환경 (`journalctl` / 향후 CloudWatch) 에서 *요청 흐름 추적* + *알림 자동화* (ERROR 즉시 페이저 / WARN 일간 검토) 를 위해 *표준 메시지 포맷 + 레벨 분리* 필요.

본 코드 구조 (`BusinessException` + `ErrorCode.HttpStatus`) 가 이미 *예외 → status* 매핑을 캡슐화 — 이를 *로그 레벨 분기* 에 *자동 활용* 가능.

향후 Monitoring Epic 의 RPS·에러율 메트릭이 *표준 INFO 로그* 와 *5xx ERROR* 패턴에 정렬 예정.

## Decision

**API 로그 표준 메시지 포맷 + status 기반 자동 WARN/ERROR 분리** 채택:

- **표준 메시지 본문**: `[API - LOG] {METHOD} {URI} {STATUS} ({DURATION}ms)` — `RequestLoggingFilter` 가 출력.
- **시작 로그**: `[API - LOG] {METHOD} {URI} START` — *진행 중 요청* 식별용.
- **레벨 분리**:
  - **INFO**: 모든 요청 1회 (START + 완료 2건). Monitoring RPS 메트릭과 1:1.
  - **WARN**: `BusinessException` 중 `code.getStatus().is4xxClientError() == true` (4xx 매핑 예외) — stack trace 없음. `MethodArgumentNotValidException`, `MaxUploadSizeExceededException` 도 WARN (사용자 입력).
  - **ERROR**: `BusinessException` 중 `is5xxServerError() == true` + catch-all `Exception` — stack trace 포함 (`log.error(msg, ex)`).
- **위치**:
  - `RequestLoggingFilter` — `nbc.profile.common.web.logging` 패키지 (ApiResponse 와 같은 *common.web* 트리, 웹 횡단 관심사).
  - 예외 로그 — `nbc.profile.common.exception.GlobalExceptionHandler` 안에서 핸들러별 명시.
- **`status` 기반 자동 분기 핵심 이유**: 새 예외 / ErrorCode 추가 시 핸들러 *무수정* — ErrorCode 의 `HttpStatus` 가 *유일한 진실 소스*.

## Alternatives Considered

### Option A. AOP 기반 비즈니스 메서드 로깅

- **Pros**: 메서드 단위 진입/이탈 자동.
- **Cons**: *지금은 필요 없는 일반화*. Filter + Handler 만으로 운영 요구 충족.
- **기각 이유**: T7 (안 하기로) — 본 Product 범위 밖.

### Option B. 예외 클래스별 명시 핸들러 분기

- **Pros**: Story 노트 원문 그대로 따름. 예외별 다른 정책 시 자유도.
- **Cons**: 예외 추가마다 핸들러 추가 — 본 코드 구조 (`BusinessException + ErrorCode`) 와 어긋남. `ErrorCode` 가 *HttpStatus* 까지 캡슐화하는데 이를 핸들러에서 *재선언* 하는 셈.
- **기각 이유**: 본 코드 구조와 정합 부족 + 유지보수 부담.

### Option C. status 기반 자동 분기 (채택)

- **Pros**: 새 예외 / ErrorCode 추가 시 핸들러 무수정. `ErrorCode.HttpStatus` 가 *단일 진실 소스* — 응답 status + 로그 레벨 정렬. 본 코드 구조와 정합.
- **Cons**: ErrorCode 의 HttpStatus 변경 시 *로그 레벨도 자동 변경* — 의도된 동작이지만 *invariant 변경 시 주의*.

## Consequences

### Positive

- 새 예외 / ErrorCode 추가 시 GlobalExceptionHandler 무수정 — 자동 WARN/ERROR.
- ErrorCode 의 HttpStatus 가 *단일 진실 소스* — 응답 status + 로그 레벨 정렬.
- Monitoring Epic 의 RPS·에러율 메트릭과 자연 정렬 (5xx ERROR 카운트가 ERROR 로그와 일치).
- `RequestLoggingFilter` 가 컨트롤러 도달 *전* 예외도 START 로그 1건은 남김 — 추적성.

### Negative / Trade-offs

- 부팅 전 예외 (Spring Boot ApplicationContext 초기화 실패 등) 는 Filter 가 못 잡음 → systemd / 콘솔 로그 의존.
- Filter `finally` 가 못 잡는 *Tomcat 이전 예외* (Content-Type 파싱 실패 등) → *완료 로그 누락*. Tomcat Access Log 미활성 상태에선 START 로그만 남음. 향후 Access Log 도입 시 보강.
- 동일 요청에 INFO 2건 (START + 완료) — Monitoring RPS 메트릭이 *Filter 호출 1회* 기준 (로그 줄수 아님) 임을 *전제*.

### Neutral

- `logback-spring.xml` 콘솔 패턴 커스터마이징은 *본 PR 미적용* — Spring Boot 기본 패턴 (`%d %-5level [%thread] %logger - %msg%n`) 으로 시간·스레드·로거명 표기 충분.
- traceId / MDC 는 *본 ADR 미적용* — 향후 Monitoring Epic 에서 *동일 포맷 자연 확장*.

## Follow-ups

- [ ] Monitoring Epic 진입 시 RPS·에러율 메트릭 정렬 검증.
- [ ] Tomcat Access Log 활성화 ADR 검토 (Filter `finally` 미포착 케이스 보강).
- [ ] traceId / MDC 도입 ADR (본 표준 포맷 자연 확장).
- *재검토 트리거*: 로그량 폭증 (예: WARN 에 stack trace 요구) 또는 *예외 클래스별 다른 정책* 필요해지면 본 결정 재평가.
