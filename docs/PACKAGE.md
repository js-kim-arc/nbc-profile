# 패키지 · BC · 레이어 구조

> 사실은 소스 트리가 진실 소스. 본 문서는 *의존 규칙*과 *왜 그렇게 나눴는가*만 담는다.

---

## 레이어 정의

| 레이어 | 책임 | 의존 가능 방향 |
| --- | --- | --- |
| **presentation** | HTTP 요청 수신 · DTO 직렬화 · 인증 컨텍스트 추출 | → `application` |
| **application** | 트랜잭션 경계 · 유즈케이스 조율 · 외부 시스템 호출 (포트 경유) | → `domain`, `application.port.out` |
| **domain** | 도메인 객체 · 불변식 · 도메인 서비스 | (없음) |
| **application.port.out** | 외부 시스템 추상화 인터페이스 (driven port) | (인터페이스만) |
| **infrastructure** | port 어댑터 구현 — DB · 외부 API · 파일 시스템 · 클라우드 | → `domain`, `application.port.out` |

원칙:
- `domain` 은 *어디에도 의존하지 않는다*. 단, JPA 어노테이션이 도메인 객체에 박히는 트레이드오프는 본 프로젝트가 *단순성 우선*으로 채택 (별도 ADR 미작성).
- `presentation` 은 `domain` 을 직접 import 하지 않는다. application 의 Command/Query record 와 Response DTO 만 알면 충분.
- `application` 의 public 메서드는 *Command / Query record 1개만* 인자로 받음 (conventions.md §2 참조).
- 인프라 의존은 *반드시 `application.port.out` 인터페이스를 통해서만*. Application Service 가 `software.amazon.awssdk.*` 등을 직접 import 하지 않는다.

---

## BC 별 패키지 매핑

루트 패키지 컨벤션: `nbc.profile.{역할}` 또는 `nbc.profile.{bc}.{layer}`.

| BC / 영역 | 루트 패키지 | 비고 |
| --- | --- | --- |
| **Member** | `nbc.profile.member` | `domain` / `repository` 하위. |
| **Shared (공통 인프라)** | `nbc.profile.shared` | BC 횡단 port + 그 어댑터 (`FileStoragePort` + S3 어댑터). |
| **Cross-cutting / Common** | `nbc.profile.common` | `exception` (전역 예외 · ErrorCode · GlobalExceptionHandler). |
| **Cross-cutting / Config** | `nbc.profile.config` | JpaAuditingConfig 등 횡단 설정. |

현재 트리:

```
nbc.profile
├── ProfileApplication                      # @SpringBootApplication
├── common
│   ├── exception
│   │   ├── BusinessException               # abstract, RuntimeException 상속
│   │   ├── ErrorCode                       # enum (status, message)
│   │   └── GlobalExceptionHandler          # @RestControllerAdvice (envelope + Validation + MaxUploadSize + catch-all)
│   └── web
│       └── ApiResponse                     # record<T>(code, message, data) — 성공/에러 통일 envelope (ADR-0005)
├── config
│   └── JpaAuditingConfig                   # @EnableJpaAuditing(dateTimeProviderRef)
├── shared
│   ├── application/port/out
│   │   ├── FileStoragePort                 # 인터페이스 (upload/download/delete/exists/presign)
│   │   └── FileStorageException            # extends BusinessException
│   └── infrastructure/storage
│       ├── S3StorageProperties             # @ConfigurationProperties record + @Validated + nested Credentials/Presigned
│       ├── S3StorageConfig                 # @Configuration + @EnableConfigurationProperties (Properties 모든 profile 등록, S3Client/Presigner Bean 만 @Profile("!test"))
│       └── S3FileStorageAdapter            # @Component @Profile("!test")
└── member
    ├── application
    │   ├── MemberService                   # @Service @Transactional (4 메서드: create/get/updateProfileImage/getProfileImageUrl)
    │   └── dto
    │       ├── MemberCreateCommand         # record (name, age, mbti String)
    │       └── ImageUploadCommand          # record (bytes, contentType, originalFilename)
    ├── domain
    │   ├── Member                          # Aggregate Root (정적 팩토리)
    │   ├── Mbti                            # 16값 enum
    │   └── exception
    │       ├── MemberDomainException       # 도메인 검증 실패
    │       ├── MemberNotFoundException     # 존재하지 않는 회원 (404)
    │       └── ProfileImageNotFoundException  # profileImageKey null 인 회원의 URL 조회 (404)
    ├── presentation
    │   ├── MemberController                # /api/members 4 endpoint
    │   └── dto
    │       ├── request/MemberCreateRequest  # record + jakarta.validation
    │       └── response
    │           ├── MemberResponse           # static from(Member)
    │           └── ProfileImageUrlResponse  # imageUrl + expiresAt
    └── repository
        └── MemberRepository                # JpaRepository<Member, Long>
```

`src/test/java/nbc/profile/shared/infrastructure/storage/InMemoryFileStorageAdapter` — `@Profile("test")`. 컴포넌트 스캔 자동 발견.

---

## BC 간 의존 규칙

- **양방향 의존 금지**.
- BC 가 *공통 인프라*가 필요하면 `shared/` 의 port 를 의존. 어댑터 구현은 모름.
- BC 간 직접 통신은 다음 중 하나:
  1. *Application Service 가 양쪽 Repository 를 모두 알고 조율*.
  2. *도메인 이벤트 + 비동기 핸들러*.
  3. *Anti-Corruption Layer*.

---

## 공통 / 횡단 관심사 (cross-cutting)

| 영역 | 위치 | 비고 |
| --- | --- | --- |
| 감사 (Auditing) | `nbc.profile.config.JpaAuditingConfig` | `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`. 테스트는 `TestDateTimeProviderConfig` 로 `@Primary` 덮어쓰기. |
| GlobalExceptionHandler · ErrorCode | `nbc.profile.common.exception` | `BusinessException` 상속 예외만 라우팅. 도메인은 `MemberDomainException` · 포트는 `FileStorageException`. |
| 파일 저장 | `nbc.profile.shared.infrastructure.storage` | S3Client / S3Presigner Bean 만 `@Profile("!test")`, Properties 는 모든 profile 에서 등록 (`S3StorageConfig` 클래스 자체는 무프로필). LocalStack 시 `app.storage.s3.endpoint` 만 채움. |
| API 응답 envelope | `nbc.profile.common.web.ApiResponse` | 성공/에러 통일 `{code, message, data}` 포맷 (ADR-0005). `GlobalExceptionHandler` 가 BusinessException · Validation · MaxUploadSize · catch-all 4 경로 매핑. |
| 인증 · 인가 | _(TODO)_ | |
| 시각 · Clock | `DateTimeProvider` Bean (JpaAuditingConfig) | 운영: `LocalDateTime.now()` · 테스트: 시퀀스 (호출마다 1ms 전진) |

---

## Profile 매트릭스

| Profile | 활성 어댑터 | 용도 |
| --- | --- | --- |
| `default` / `local` / `prod` | `S3FileStorageAdapter` | 실 운영 또는 LocalStack 개발 |
| `test` | `InMemoryFileStorageAdapter` (`src/test`) | 테스트 격리 (Spring Test 컨텍스트 로드 시 S3 자격증명 의존 회피) |

동시 활성 어댑터는 *항상 1개*.

---

## 결정의 흔적 (ADR 링크)

- (없음 — 첫 ADR 작성 시 추가)

---

## 갱신 룰

- 레이어 분할 · BC 신설 / 통합 → 본 문서 즉시 갱신 + ADR 의무.
- 의존 규칙 변경 → ADR 동반 + 본 문서 갱신.
- 단발 파일 추가 / 이동 → 본 문서 갱신 불필요 (소스 트리가 진실 소스).
