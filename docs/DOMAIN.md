# 도메인 의도

> 본 문서는 *코드만으론 알 수 없는 의도*만 담는다. API 명세 · DB 스키마 · 테스트 매트릭스는 코드가 진실 소스.
>
> 본 파일이 1500줄을 초과하기 시작하면 BC 별 파일 분할(`docs/domain/{bc}.md`) 재도입 검토.

---

## 프로젝트 개요

사용자 프로필 관리 백엔드 — 회원 기본 정보(이름 · 나이 · MBTI)와 프로필 이미지 메타데이터 관리. 클라우드 주차 과제 (Spring Boot + JPA + H2/MySQL).

## 핵심 가치 · 비기능 요구

- **인프라 격리**: 도메인 객체는 URL · 파일 시스템 · 클라우드 스토리지를 *알지 않는다*. 프로필 이미지는 *식별자(key)* 만 보관.
- **포트-어댑터 분리**: 인프라 의존은 `domain.port` 인터페이스로만 표현. Service 가 *어떤 어댑터인지 모름* — Profile 분기로 어댑터 교체.
- **MySQL 이전 호환**: H2 `MODE=MySQL` 로 개발 중에도 표준 JPA 매핑 강제. H2 전용 함수·타입 사용 금지.
- **에러 응답 일관성**: 모든 도메인·인프라 예외는 `BusinessException` 상속 + `ErrorCode` 등록 → `GlobalExceptionHandler` 단일 진입점에서 `{code, message}` 형식 응답.

---

## Bounded Context 목록

| BC | 책임 | 핵심 Aggregate | 비고 |
| --- | --- | --- | --- |
| **Member** | 회원 식별 · 기본 정보 · 프로필 이미지 식별자 보관 | `Member` | Story 1-1 에서 도입 |

---

## 공통 용어 (Ubiquitous Language)

| 용어 | 정의 | 비고 |
| --- | --- | --- |
| Member | 시스템에 등록된 사용자. 이름 · 나이 · MBTI · 프로필 이미지 키를 가짐. | |
| ProfileImageKey | 프로필 이미지의 *식별자 (string)*. URL 이 아니다. 인프라 어댑터가 해석 책임을 진다. | nullable |
| MBTI | 16값 enum. 변경 가능성 있으나 현재 고정. | `Mbti` enum |

---

## BC 별 의도 · 불변식

### Member

#### 책임 (한 줄)

회원 식별 · 기본 정보 · 프로필 이미지 식별자 보관. *URL 생성 · 파일 입출력은 알지 않는다*.

#### 핵심 용어

- `Member` — Aggregate Root.
- `Mbti` — 16값 enum 도메인 VO.
- `profileImageKey` — 외부 스토리지의 식별자 문자열 (URL 이 아님).

#### 불변식

- `name` 은 필수 · trim 후 blank 금지. 길이 50 이내.
- `mbti` 는 16값 enum 중 하나. null 금지.
- `profileImageKey` 는 nullable. blank · 공백 문자열은 null 로 정규화.
- 동일 `profileImageKey` 재설정은 *no-op* (멱등). `updatedAt` 도 갱신되지 않는다.
- `Member` 는 `URL` · `File` · `MultipartFile` · `S3` 같은 인프라 타입을 import 하지 않는다. 프로필 이미지 저장은 `ImageStorage` 포트를 통해 분리되어 있다.
- `age` 는 *비음수* 보장 — 도메인 검증 (`MEMBER_AGE_OUT_OF_RANGE`) + DB `int NOT NULL` 이중 방어. 상한 범위 검증은 추후 Story.
- `id` · `createdAt` · `updatedAt` 외부 주입 금지 — JPA / Auditing 으로만 부여.
- 도메인 검증 실패 시 `MemberDomainException(ErrorCode.MEMBER_*)` 만 던진다 — `IllegalArgumentException` 같은 stdlib 예외는 금지.

#### 상태 전이

- 본 BC 는 *상태 전이 없음* (Story 1-1 범위). `profileImageKey` 는 값 교체일 뿐 상태 전이가 아니다.

#### 다른 BC 와의 관계

- `FileStoragePort` (`shared/`) 가 `profileImageKey` 의 생성·해석 책임. Member 는 *결과 key (String) 만 받아 보관*하고 인프라 해석에 의존하지 않는다.

---

## 포트 / 어댑터

| 포트 | 위치 | 책임 | 어댑터 |
| --- | --- | --- | --- |
| `FileStoragePort` | `nbc.profile.shared.application.port.out` | 파일 업로드 · 다운로드 · 삭제 · 존재 확인 · presigned URL 생성. *공통 인프라* 라 BC 횡단 (Member 외 추후 BC 도 동일 포트 사용). | `S3FileStorageAdapter` (`@Profile("!test")`, AWS SDK v2) · `InMemoryFileStorageAdapter` (`@Profile("test")`, `src/test`) |

원칙:
- 포트 시그니처는 *최소한 · 표준 타입만* (String key · byte[] · String contentType · URL · Duration). AWS SDK / S3 전용 개념(bucket · ACL · presigner)은 노출하지 않는다.
- `Duration expiration` 은 *모든 어댑터에 전달*되지만 어댑터가 *해석은 자유*. S3 는 presigned URL 만료. In-memory 는 무시.
- 동시 활성 어댑터는 *항상 1개* — `@Profile("!test")` vs `@Profile("test")` 로 분기.
- LocalStack 사용 시 `app.storage.s3.endpoint` 만 채워 S3 어댑터 그대로 사용.
- 인프라 예외는 `FileStorageException(ErrorCode.FILE_STORAGE_*)`. Application Service 는 try-catch 없음 — `GlobalExceptionHandler` 가 매핑.

---

## 결정의 흔적 (ADR 링크)

- (없음 — 첫 ADR 작성 시 추가)

---

## 갱신 룰

- BC 신설 / 책임 변경 → 본 문서 즉시 갱신.
- 불변식 변경 → ADR 동반 후 본 문서 갱신 (`docs(adr): ...` 또는 같은 PR).
- 단발 버그 fix · 컨벤션 따른 변경 → 본 문서 갱신 불필요.
