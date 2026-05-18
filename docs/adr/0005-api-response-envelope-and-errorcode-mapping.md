# ADR-0005: ApiResponse envelope 통일 + ErrorCode 매핑 정책 + URL prefix

- **Status**: Accepted
- **Date**: 2026-05-18
- **Deciders**: junseong kim

## Context

Story 3-1 진입으로 4 API (회원 생성/조회 + 프로필 이미지 업로드/URL 조회) 가 *외부에 처음 노출* 된다. 다음 결정이 *동시에* 필요:

1. **응답 형식** — 성공 / 에러 응답을 *같은 형태* 로 감쌀까 vs *raw payload + 에러는 별도 형태*?
2. **ErrorCode 세부도** — 기존 `MEMBER_NAME_BLANK` 같은 *도메인 검증 세부 코드* 와 API 입력 검증 `VALIDATION_FAILED` 같은 *포괄 코드* 를 어떻게 공존시킬지?
3. **URL prefix** — `.claude/rules/conventions.md` §2.1 은 `/api/v1` 권장. `workflow/story/story.md` 명세는 `/api/members` (v1 없음). 어느 쪽?

세 결정 모두 *외부 계약* 에 박힌다. 모두 trigger T1·T3.

## Decision

### 1. ApiResponse envelope 통일

모든 응답을 `nbc.profile.common.web.ApiResponse<T>(String code, String message, T data)` 단일 record 로 감싼다.

- **성공**: `("SUCCESS", "OK", data)` — 정적 팩토리 `ApiResponse.success(data)`.
- **에러**: `(ErrorCode.name(), code.getMessage(), null|payload)` — 정적 팩토리 `ApiResponse.error(code)` / `ApiResponse.error(code, payload)`. payload 는 Validation 필드 에러 목록 같은 *부가 정보* 용.

### 2. ErrorCode 세부도 공존

기존 도메인 검증 ErrorCode (`MEMBER_NAME_BLANK`, `MEMBER_MBTI_NULL`, `MEMBER_AGE_OUT_OF_RANGE`) 와 신규 API 코드 (`VALIDATION_FAILED`, `MEMBER_NOT_FOUND`, `PROFILE_IMAGE_NOT_FOUND`, `FILE_TOO_LARGE`) 를 *공존* 시킨다.

- Bean Validation (@Valid + @NotBlank/@Min/@Pattern) 실패 → `VALIDATION_FAILED` + 필드 에러 List.
- Bean Validation 우회 (직접 도메인 호출, 다른 진입점) 시 도메인 검증 실패 → `MEMBER_NAME_BLANK` 등 세부 코드.
- 결과: 외부 API 호출은 *대부분 `VALIDATION_FAILED`* 받지만, *이중 방어* 정신은 보존.

### 3. URL prefix: `/api/members` 채택

`.claude/rules/conventions.md` §2.1 의 `/api/v1` 권장과 *충돌* 하지만 명세 우선. 룰 갱신은 Follow-up.

## Alternatives Considered

### Envelope: Option A — Raw payload (envelope 없음)

- Pros: 단순. 페이로드 크기 최소.
- Cons: 성공/에러 응답이 *완전히 다른 형태* 라 클라이언트가 *어떤 형태인지 매번 분기* 해야. 응답 형식 일관성 0.
- 기각 이유: 클라이언트 분기 비용 누적.

### Envelope: Option B — envelope wrapping (채택)

- Pros: 클라이언트가 *항상 `code/message/data` 3 키* 만 알면 됨. 성공·에러 동일 코드 경로.
- Cons: 페이로드 약간 증가. data null 응답이 빈 payload 보다 verbose.
- 기각 이유: 없음 — 채택.

### Envelope: Option C — RFC 7807 Problem Details (`application/problem+json`)

- Pros: 표준. 에러 응답의 산업 표준.
- Cons: *에러에만* 적용되는 표준이라 성공 응답은 별도 형식 — Option A 와 같은 분기 문제.
- 기각 이유: 성공/에러 통일 의도와 충돌.

### ErrorCode 세부도: Option A — 도메인 검증 코드 폐기, API 포괄 코드만

- Pros: 단순. 외부 노출 코드 4~5 개만.
- Cons: 도메인 객체가 던지는 예외에 *의미 없는 일반 코드* 만 매핑 — 도메인 의도 손실.
- 기각 이유: 이중 방어 정신 위배.

### ErrorCode 세부도: Option B — 공존 (채택)

- Pros: 도메인 검증의 의도 보존 + API Validation 의 포괄 코드 모두 활용.
- Cons: ErrorCode enum 이 *두 영역의 코드 혼재* — 정리 부담.
- 기각 이유: 없음 — 채택.

### URL prefix: Option A — `/api/v1/members` (룰 §2.1 준수)

- Pros: 룰 일관성. 향후 v2 자연.
- Cons: 명세와 충돌. *사용자 명시 결정* (`/api/members`) 우선.
- 기각 이유: 명세 우선.

### URL prefix: Option B — `/api/members` (채택)

- Pros: 명세 그대로. 클라이언트 정의 호환.
- Cons: 룰 §2.1 위배. 향후 v2 도입 시 *마이그레이션* 필요.
- 기각 이유: 없음 — 채택. 룰 §2.1 갱신 Follow-up.

## Consequences

### Positive

- 응답 형식 *완전 일관성* — 성공/에러 코드 경로 통일.
- 도메인 검증 의도 보존 (이중 방어).
- 명세 일치 — 클라이언트 정의 그대로 사용.

### Negative / Trade-offs

- **룰 §2.1 위배** — 본 ADR 이 *룰 위배 결정 첫 사례*. 추후 룰을 갱신하거나 본 결정을 재평가해야.
- ErrorCode enum 이 *두 세부도* 코드 혼재 — 운영 누적 시 정리 부담.
- ApiResponse 의 `code/message` 가 *매직 문자열* (`"SUCCESS"` · `"OK"` · `"INTERNAL_ERROR"`) — ErrorCode enum 으로 옮기는 결정은 v2.

### Neutral

- `ApiResponse<Void>` 형태가 *제네릭 + null data* 로 표현됨. Jackson 직렬화 시 `data: null` 명시.
- `MethodArgumentNotValidException` handler 가 `ApiResponse<List<String>>` 반환 — 응답 형태가 path 별로 *data 타입* 다양.

## Follow-ups

- [ ] `.claude/rules/conventions.md` §2.1 갱신 — `/api/v1` 강제 → "신규 API 는 v1 prefix 권장, 본 프로젝트는 `/api/members` 채택 (ADR-0005 참조)" 보강. 별도 PR.
- [ ] `ApiResponse.success(...)` 의 `code`/`message` 문자열 상수를 `ErrorCode` 또는 별도 enum 으로 추출 검토.
- [ ] *재검토 트리거*: v2 API 도입 필요 시 — 본 결정 재평가.
- [ ] *재검토 트리거*: ErrorCode 가 30 개 이상 누적 시 — 도메인/API 영역 분리 또는 enum 클래스 분할 검토.
