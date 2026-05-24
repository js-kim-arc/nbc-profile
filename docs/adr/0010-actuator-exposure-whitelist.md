# ADR-0010: Actuator endpoint 노출 화이트리스트 + show-details:never

- **Status**: Accepted
- **Date**: 2026-05-19
- **Deciders**: junseong kim
- **Trigger**: T4 (외부 의존성 도입), T5 (보안 트레이드오프), T7 (when-authorized 미적용)

## Context

`spring-boot-starter-actuator` 신규 도입 — ALB Health Check + 향후 Monitoring Epic 의 표준 기반.

Actuator 기본 활성 endpoint 다수 (env · beans · heapdump · threaddump · configprops · mappings · health 등) — *익명 노출 시 fingerprinting* + *환경 변수 누설* + *heap dump 다운로드* 위험.

본 프로젝트는 *Spring Security 미도입* — 인증 기반 제어 (`when-authorized`) 불가.

## Decision

**명시 화이트리스트 (`include: health`) + 이중 안전망 블랙리스트 (`exclude: env, beans, ...`) + `show-details: never`** 채택:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
        exclude: env, beans, heapdump, threaddump, configprops, mappings
      base-path: /actuator
  endpoint:
    health:
      show-details: never
      probes:
        enabled: false
```

- `show-details: never` — UP/DOWN 만 응답. *DB 상태·디스크 정보 익명 노출 차단*.
- `probes.enabled: false` — K8s liveness/readiness 분리는 *지금 미적용* (T7 — 본 PR 범위 외).
- `include` 와 `exclude` 의 중복 정의처럼 보이지만 의도적 — *명시성* 이 *최선의 방어*.

`RequestLoggingFilter` 는 `/actuator/*` 경로를 *제외* (ADR-0009 정신 — 헬스체크 폴링이 로그 노이즈 되지 않도록).

## Alternatives Considered

### Option A. 기본 (env/heapdump 등 익명 노출)

- **Pros**: 디폴트 — 운영 디버깅 편의.
- **Cons**: 본 프로젝트 미인증 환경에서 *fingerprinting* + *시크릿 누설* 위험.
- **기각 이유**: 보안.

### Option B. `include` 만 (화이트리스트 단독)

- **Pros**: 단순.
- **Cons**: 향후 *include 에 새 endpoint 1줄 추가* 실수 시 노출 위험. 이중 안전망 부족.
- **기각 이유**: 안전성.

### Option C. `include` + `exclude` 이중 (채택)

- **Pros**: 명시 화이트리스트 + 방어 블랙리스트. *include 에 실수로 더 추가해도 exclude 가 잡음*.
- **Cons**: 중복 정의 외관. 무관 — 명시성이 안전성.

### Option D. `when-authorized` + Spring Security

- **Pros**: 인증 사용자에게만 details 노출.
- **Cons**: Spring Security 미도입 — 본 PR 범위 외.
- **기각 이유**: T7. Security 도입 시 재평가.

## Consequences

### Positive

- ALB Health Check + Monitoring 모두 *200 OK 만 필요* — show-details 불필요.
- 익명 호출자 fingerprinting 차단.
- 향후 Monitoring Epic 에서 `/actuator/prometheus` *include 1줄 추가* 만으로 확장 (`include: health,prometheus` 또는 별도 노출).

### Negative / Trade-offs

- Monitoring Epic 에서 prometheus 추가 시 *본 ADR 갱신* 또는 *후속 ADR* 필요.
- K8s 환경 도입 시 `probes.enabled: true` 재평가 (T7).
- 인증 도입 시 `show-details: when-authorized` 재평가.

### Neutral

- Actuator 의존성 추가만으로 *Micrometer 자동 설정* 등록 — `/actuator/prometheus` 는 *exclude 되어 노출 안 됨*. Monitoring Epic 에서 *include 추가 1줄* 변경.
- `application.yml` 의 management 설정은 *공통* 위치 (모든 profile 공유).

## Follow-ups

- [ ] Monitoring Epic 진입 시 prometheus include 추가 + 본 ADR 갱신 or 후속 ADR.
- [ ] Spring Security 도입 시 `show-details: when-authorized` 재평가.
- [ ] K8s 도입 시 probes 분리.
- *재검토 트리거*: 인증 시스템 도입 / 운영 모니터링 요구 변경 / Spring Boot Actuator 디폴트 정책 변경.
