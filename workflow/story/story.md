## Story 3-1. 4개 API · 일관된 응답 포맷 · `@WebMvcTest` + 통합 테스트

### User Story

> As a 백엔드 개발자,
I want 4개 API가 일관된 응답 포맷으로 동작하고 단위 / 통합 테스트로 보장되길,
So that 과제 채점 기준을 만족하면서, Product 5에서 S3 어댑터로 교체해도 *API 계약과 테스트가 그대로 통과*한다.
>

### 설계 노트

- `ApiResponse.java`

    ```java
    public record ApiResponse<T>(String code, String message, T data) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>("SUCCESS", "OK", data);
        }
        public static <T> ApiResponse<T> error(ErrorCode code, String message) {
            return new ApiResponse<>(code.name(), message, null);
        }
    }
    ```

- `ErrorCode.java`

    ```java
    public enum ErrorCode {
        VALIDATION_FAILED,
        MEMBER_NOT_FOUND,
        PROFILE_IMAGE_NOT_FOUND,
        FILE_TOO_LARGE,
        UNSUPPORTED_FILE_TYPE,
        STORAGE_FAILURE,
        INTERNAL_ERROR
    }
    ```

- `MemberController.java`

    ```java
    @RestController
    @RequestMapping("/api/members")
    @RequiredArgsConstructor
    public class MemberController {
    
        private final MemberService memberService;
    
        @PostMapping
        public ResponseEntity<ApiResponse<MemberResponse>> create(
                @Valid @RequestBody MemberCreateRequest req) {
            var resp = memberService.create(req.toCommand());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
        }
    
        @GetMapping("/{id}")
        public ApiResponse<MemberResponse> get(@PathVariable Long id) {
            return ApiResponse.success(memberService.get(id));
        }
    
        @PostMapping(value = "/{id}/profile-image", consumes = MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<MemberResponse> uploadProfileImage(
                @PathVariable Long id,
                @RequestParam("file") MultipartFile file) throws IOException {
            var cmd = new ImageUploadCommand(
                    file.getBytes(), file.getContentType(), file.getOriginalFilename());
            return ApiResponse.success(memberService.updateProfileImage(id, cmd));
        }
    
        @GetMapping("/{id}/profile-image")
        public ApiResponse<ProfileImageUrlResponse> getProfileImageUrl(@PathVariable Long id) {
            return ApiResponse.success(memberService.getProfileImageUrl(id, Duration.ofDays(7)));
        }
    }
    ```

- `MemberService.java`

    ```java
    @Service
    @RequiredArgsConstructor
    @Transactional
    public class MemberService {
    
        private final MemberRepository memberRepository;
        private final ImageStorage imageStorage;
    
        public MemberResponse create(MemberCreateCommand cmd) {
            var member = memberRepository.save(
                    Member.builder().name(cmd.name()).age(cmd.age()).mbti(cmd.mbti()).build());
            return MemberResponse.from(member);
        }
    
        @Transactional(readOnly = true)
        public MemberResponse get(Long id) {
            return MemberResponse.from(findOrThrow(id));
        }
    
        public MemberResponse updateProfileImage(Long id, ImageUploadCommand cmd) {
            var member = findOrThrow(id);
            var key = imageStorage.upload(cmd.bytes(),
                    new ImageMetadata(cmd.contentType(), cmd.originalFilename()));
            member.updateProfileImageKey(key.value());
            return MemberResponse.from(member);
        }
    
        @Transactional(readOnly = true)
        public ProfileImageUrlResponse getProfileImageUrl(Long id, Duration ttl) {
            var member = findOrThrow(id);
            if (member.getProfileImageKey() == null) {
                throw new ProfileImageNotFoundException(id);
            }
            var url = imageStorage.generateDownloadUrl(
                    new StorageKey(member.getProfileImageKey()), ttl);
            return new ProfileImageUrlResponse(url.toString(),
                    LocalDateTime.now().plus(ttl));
        }
    
        private Member findOrThrow(Long id) {
            return memberRepository.findById(id)
                    .orElseThrow(() -> new MemberNotFoundException(id));
        }
    }
    ```

- `GlobalExceptionHandler.java`

    ```java
    @RestControllerAdvice(basePackages = "com.thirdtool.member.api")
    @Slf4j
    public class GlobalExceptionHandler {
    
        @ExceptionHandler(MemberNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleNotFound(MemberNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ErrorCode.MEMBER_NOT_FOUND, e.getMessage()));
        }
    
        @ExceptionHandler(ProfileImageNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleImageNotFound(ProfileImageNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ErrorCode.PROFILE_IMAGE_NOT_FOUND, e.getMessage()));
        }
    
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<List<String>>> handleValidation(
                MethodArgumentNotValidException e) {
            var errors = e.getBindingResult().getFieldErrors().stream()
                    .map(f -> "%s: %s".formatted(f.getField(), f.getDefaultMessage()))
                    .toList();
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(ErrorCode.VALIDATION_FAILED.name(),
                            "Validation failed", errors));
        }
    
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE, "File exceeds 5MB"));
        }
    
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
            log.error("Unhandled exception", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Internal server error"));
        }
    }
    ```

- `MemberCreateRequest.java`

    ```java
    public record MemberCreateRequest(
            @NotBlank @Size(max = 50) String name,
            @Min(0) @Max(150) int age,
            @NotBlank @Pattern(regexp = "[EI][SN][TF][JP]") String mbti) {
        public MemberCreateCommand toCommand() {
            return new MemberCreateCommand(name, age, mbti);
        }
    }
    ```


### 완료 기준 (Acceptance Criteria)

- [ ]  `POST /api/members` 정상 호출 시 201 + `ApiResponse<MemberResponse>`
- [ ]  `GET /api/members/{id}` 존재 시 200, 미존재 시 404 + `MEMBER_NOT_FOUND`
- [ ]  `POST /api/members/{id}/profile-image` 정상 호출 시 200, *디스크에 파일 저장*, DB의 `profile_image_key` 갱신
- [ ]  `GET /api/members/{id}/profile-image`가 URL + 만료시각을 반환
- [ ]  프로필 이미지 미등록 회원에 대한 URL 조회 시 404 + `PROFILE_IMAGE_NOT_FOUND`
- [ ]  `@Valid` 위반 시(빈 name, MBTI `"XXXX"`) 400 + 필드별 에러 목록
- [ ]  6MB 파일 업로드 시 413 + `FILE_TOO_LARGE`
- [ ]  `grep -r "MultipartFile" src/main/java/.../service/` 결과 0건

### 엣지 케이스

- 한글 파일명(`프로필.png`) 업로드 → 정상 저장, `originalFilename`이 한글 그대로 보존
- 확장자 없는 파일명(`profile`) 업로드 → 어댑터가 `.bin`으로 처리. *재현 확인 후 v2에서 거부 정책 ADR화 검토*
- 동일 회원에게 이미지 2번 업로드 → 두 번째 호출이 `profile_image_key`를 덮어씀. *이전 파일은 디스크에 남음* (정리는 v2)
- 프로필 이미지 미등록 회원의 URL 조회 → `ProfileImageNotFoundException` → 404
- `MultipartFile`이 빈 파일(`isEmpty() == true`) → Controller에서 사전 검증 후 400 + `VALIDATION_FAILED`
- `Content-Type`이 이미지 계열이 아님 (`text/plain`) → *과제 범위에선 거부 안 함*, 어댑터는 저장. *v2에서 화이트리스트 검증 ADR화*

### Definition of Done

- [ ]  코드 리뷰 완료
- [ ]  `MemberControllerTest` (`@WebMvcTest`) 그린 — Service mock 사용
- [ ]  `MemberApiIntegrationTest` (`@SpringBootTest @ActiveProfiles("test")`) 그린 — in-memory 어댑터로 교체 검증
- [ ]  Postman 컬렉션 4개 시나리오 (성공 + 404 + 400 + 413) 첨부
- [ ]  `curl` 4개 API 응답 스크린샷 첨부
- [ ]  `grep` 결과(`MultipartFile`, `AmazonS3` Service 0건) 캡처