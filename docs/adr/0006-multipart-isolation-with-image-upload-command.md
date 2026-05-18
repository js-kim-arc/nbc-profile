# ADR-0006: Multipart 격리 — Controller-only 경계, Application 은 `ImageUploadCommand` 만 인지

- **Status**: Accepted
- **Date**: 2026-05-18
- **Deciders**: junseong kim

## Context

Story 3-1 의 `POST /api/members/{id}/profile-image` 가 `multipart/form-data` 로 파일을 받는다. Spring Web MVC 의 `MultipartFile` 추상은 *프레임워크 종속 타입*. 도메인 / Application Service 가 이걸 인지하면 헥사고날 원칙 위배.

## Decision

`MultipartFile` 은 **`MemberController` 에서만 import**. Controller 가 `file.getBytes()`, `file.getContentType()`, `file.getOriginalFilename()` 으로 *즉시 변환* 하여 `ImageUploadCommand(byte[] bytes, String contentType, String originalFilename)` record 로 Application Service 에 전달한다.

Application / 도메인 / 인프라 어댑터 어디에도 `MultipartFile` import 0.

## Alternatives Considered

### Option A: Service 가 `MultipartFile` 직접 수신

- Pros: Controller boilerplate 0.
- Cons: Spring Web MVC 의존성이 Application 까지 침투 — 헥사고날 격리 위배. Service 단위 테스트 시 `MultipartFile` Mock 필요 (복잡).
- 기각 이유: 격리 위배.

### Option B: Controller 가 `ImageUploadCommand` record 변환 (채택)

- Pros: Service 는 *순수 Java 타입* 만 인지. 단위 테스트 단순.
- Cons: Controller 한 줄 변환 코드.
- 기각 이유: 없음 — 채택.

### Option C: Domain 객체 (`Member`) 가 `MultipartFile` 인지

- Pros: 없음.
- Cons: 도메인이 *프레임워크* 알게 됨. 최악의 격리 위배.
- 기각 이유: 즉시 기각.

### Option D: `MultipartFile` 을 *Stream* 으로 받아 어댑터까지 전달

- Pros: 메모리 효율 (큰 파일).
- Cons: 본 Story 5 MB 한도 — 무관. 추상 복잡도 증가.
- 기각 이유: 본 Story 범위 초과. 향후 large file 필요 시 별도 결정.

## Consequences

### Positive

- 도메인 / Application 격리.
- Service 단위 테스트 시 *순수 byte[] + String 2 개* 만 stub.
- 향후 어댑터 (HTTP·CLI·event-driven) 가 *Command 만 만들면 됨* — 진입점 다양화 자연.

### Negative / Trade-offs

- `file.getBytes()` 가 *전체 바이트를 메모리에 로드* — 5 MB 한도라 무관하지만 *향후 large file* 시 메모리 부담.
- Controller 가 *변환 책임* — 변환 로직 (예: originalFilename null 처리) 이 Controller 에 박힘. 한 곳에 응집.

### Neutral

- `IOException` 이 Controller 메서드 시그니처에 노출 (`file.getBytes()` throws). Spring 의 기본 핸들러로 500 처리.

## Follow-ups

- [ ] AC 검증: `grep "MultipartFile" src/main/java/nbc/profile/member/application/` → 0 건.
- [ ] AC 검증: `grep "MultipartFile" src/main/java/nbc/profile/member/domain/` → 0 건.
- [ ] *재검토 트리거*: 100 MB 이상 large file 업로드 케이스 발생 시 — streaming 어댑터 (예: `Resource` 또는 `InputStream`) 검토.
- [ ] 빈 파일 (`MultipartFile.isEmpty()`) 거부 검증을 Controller 에 추가 — *후속 보강* (Story 3-1 명세 엣지 케이스).
