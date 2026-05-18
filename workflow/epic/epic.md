# Epic 3. 4개 API + 일관된 응답 포맷 — Controller · DTO · 전역 ExceptionHandler

## Epic 목표

> 과제에서 요구하는 4개 API(`POST /api/members`, `GET /api/members/{id}`, `POST /api/members/{id}/profile-image`, `GET /api/members/{id}/profile-image`)가 동작하고, *모든 응답*(성공/에러)이 `ApiResponse<T>` 포맷으로 통일된 상태를 만든다.
>

## 배경

- 컨트롤러는 *Epic 1의 영속화*와 *Epic 2의 어댑터*를 *조립하는 레이어* — Epic 3은 본 Product의 *외부 인터페이스 결정*
- 일관된 응답 포맷이 *지금* 박혀야, 향후 Actuator / S3 / CloudFront에서 추가되는 에러 케이스들이 *동일 포맷으로 자연 확장*됨
- `MultipartFile`을 Service까지 가져가지 않는 결정은 *컨트롤러를 얇게 유지*하는 컨벤션의 출발점

## 핵심 설계 결정

> **응답 envelope은 `ApiResponse<T>` 단일 포맷.**
성공 / 에러 모두 동일 구조.
>
> - 성공: `{ "code": "SUCCESS", "message": "OK", "data": { ... } }`
> - 에러: `{ "code": "MEMBER_NOT_FOUND", "message": "Member not found: id=42", "data": null }`
> - `code`는 *내부 에러 코드 enum* — HTTP status와 별개. 클라이언트가 *지역화 / 분기* 용도로 사용
> - HTTP status는 *별도* — 에러 코드와 1:N 매핑

> **MultipartFile은 컨트롤러에서 *변환*하고 Service에는 `ImageUploadCommand`로 전달.**
Service / 도메인은 `MultipartFile`을 모름.
>
> - 컨트롤러:
    >
    >     ```java
>     ImageUploadCommand cmd = new ImageUploadCommand(
>         file.getBytes(),
>         file.getContentType(),
>         file.getOriginalFilename()
>     );
>     memberService.updateProfileImage(memberId, cmd);
>     ```
>

> **`GET /api/members/{id}/profile-image`는 *조회 시점에 URL을 생성*해 반환한다.**
이미지 바이트를 직접 stream하지 않는다.
>
> - 응답: `{ "data": { "imageUrl": "...", "expiresAt": "..." } }`
> - 클라이언트는 받은 URL로 *직접 다운로드*
> - 이유: 본 Product에서는 로컬 정적 리소스 URL이지만, Product 5에서 *S3 Presigned URL*로 자연스럽게 확장. *API 계약은 변하지 않음*

## 완료 기준 (Definition of Done)

- [ ]  4개 API가 *Postman 컬렉션*으로 모두 성공
- [ ]  모든 응답이 `ApiResponse<T>` 포맷
- [ ]  `@Valid` 위반 시 400 + 위반 필드 목록 반환
- [ ]  존재하지 않는 Member ID 조회 시 404 + `MEMBER_NOT_FOUND` 코드
- [ ]  5MB 초과 업로드 시 413 + `FILE_TOO_LARGE` 코드
- [ ]  Service 계층 import에 `MultipartFile`이 0건
- [ ]  연결된 Story가 모두 Done 상태다

## 산출물 (Deliverables)

- `MemberController.java`
- `MemberService.java`
- `MemberCreateRequest.java`, `MemberResponse.java`, `ProfileImageUrlResponse.java`
- `ApiResponse.java`, `ErrorCode.java`
- `GlobalExceptionHandler.java`
- `ImageUploadCommand.java`, `MemberCreateCommand.java`
- `MemberControllerTest.java` (`@WebMvcTest`)
- `MemberApiIntegrationTest.java` (`@SpringBootTest`)

## 연결된 Story 목록

- [ ]  Story 3-1. 4개 API · 일관된 응답 포맷 · `@WebMvcTest` + 통합 테스트 (5 SP)

## 내부 메모 / 제약 사항

- `@Valid`의 `MethodArgumentNotValidException`은 *필드별 에러 메시지 배열*로 변환 — 클라이언트 친화적
- `MaxUploadSizeExceededException`은 *Servlet 컨테이너* 레벨에서 발생 → `GlobalExceptionHandler`가 받아 413 매핑
- `@ControllerAdvice`가 *Actuator 경로*를 가로채지 않도록 `basePackages = "com.thirdtool.member.api"` 명시 — 향후 Actuator 도입 시(Product 3) 충돌 방지

---