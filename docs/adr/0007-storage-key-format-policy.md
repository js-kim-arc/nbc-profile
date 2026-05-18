# ADR-0007: Storage key 생성 정책 — `profile/{memberId}/{uuid}.{ext}`

- **Status**: Accepted
- **Date**: 2026-05-18
- **Deciders**: junseong kim

## Context

`FileStoragePort.upload(String key, byte[], String contentType)` 시그니처상 *호출자가 key 를 생성* 해 전달한다. Application Service (`MemberService`) 가 *어떤 형식으로 key 를 만들지* 가 결정 대상.

key 형식은 *DB 의 `Member.profileImageKey` 컬럼에 박힌다* — 형식 변경 시 *전체 데이터 마이그레이션* 필요. T2 one-way door.

## Decision

Storage key 는 `MemberService.generateKey(memberId, originalFilename)` 헬퍼에서 다음 형식으로 생성:

```
profile/{memberId}/{UUID.randomUUID()}.{ext}
```

- `memberId`: long, 회원별 폴더 격리.
- `UUID.randomUUID()`: 128-bit 랜덤, 충돌 회피.
- `ext`: originalFilename 의 마지막 `.` 이후 문자열. null / blank / dot 없음 / dot 으로 끝남 → `"bin"` fallback.

## Alternatives Considered

### Option A: UUID 만 (`{uuid}.{ext}`)

- Pros: 가장 단순. flat 구조 — 클라우드 스토리지 partition 친화 (S3 의 prefix 분산).
- Cons: 운영 시 *어느 회원 자산인지* key 만으론 식별 불가. delete-by-member 같은 일괄 작업 불가.
- 기각 이유: 운영 가시성 손실.

### Option B: `profile/{memberId}/{uuid}.{ext}` (채택)

- Pros: 회원별 폴더 분리 — 운영 도구 (cli, dashboard) 에서 회원 자산 시각화 자연. UUID 로 동일 회원 다중 업로드 충돌 회피. 확장자 보존.
- Cons: memberId 가 *키에 노출* — presigned URL 의 path 에 `memberId` 가 보임 (사용자 본인 ID 추정 가능).
- 기각 이유: 없음 — 채택. ID 노출 위험은 *PII 가 아님* 으로 수용.

### Option C: 어댑터가 key 생성 후 반환

- Pros: 호출자 부담 0.
- Cons: 현재 `FileStoragePort.upload(key, bytes, contentType): URL` 시그니처와 충돌 — 인터페이스 자체를 변경해야. ADR-0004 + ADR-0005 결정 영향.
- 기각 이유: 시그니처 변경 비용.

### Option D: 해시 기반 (`profile/{memberId}/{sha256(bytes)}.{ext}`)

- Pros: 동일 바이트 → 동일 key (자연스러운 dedup).
- Cons: 호출 시 *전체 바이트 해시 계산* CPU 부담. dedup 이 본 Story 요구 아님.
- 기각 이유: 과한 추상화.

## Consequences

### Positive

- 운영 가시성 — `profile/{memberId}/` prefix 로 회원 자산 일괄 조회·삭제.
- UUID 로 동일 회원 *N 회 업로드* 충돌 0.
- 확장자 보존 — S3 console 에서 *파일 형식* 즉시 식별.

### Negative / Trade-offs

- **DB 에 full path 박힘** — key 형식 변경 시 *전체 데이터 마이그레이션* (예: `profile/{tenantId}/{memberId}/...` 멀티 테넌트 도입 시).
- **memberId 노출** — presigned URL 의 path 에서 본인 ID 추정 가능. PII 가 아니지만 *외부 ID 노출 정책* 차원에서 향후 *opaque ID* (UUID 매핑) 로 전환 검토 여지.
- **이전 이미지 잔존** — 동일 회원이 2회 업로드 시 *이전 key 는 자동 삭제 안 됨*. 본 Story 범위 밖 (명세 v2 노트).

### Neutral

- ext fallback `"bin"` 이 *content-type 과 일치 안 할* 수 있음 (예: `image/png` 인데 `bin`). 호출자가 *originalFilename 으로만 ext 판정* 하기 때문. 향후 *content-type 기반 ext 매핑* 으로 강화 가능.

## Follow-ups

- [ ] *재검토 트리거*: key 형식 변경 요구 발생 시 (멀티 테넌트, opaque ID 등) 본 결정 재평가 + 마이그레이션 ADR 별도 작성.
- [ ] 이전 이미지 자동 삭제 정책 — v2 ADR 후보 (`profile_image_key` 변경 시 이전 key delete).
- [ ] Content-Type → ext 화이트리스트 정책 — v2 ADR 후보 (image/png → png, image/jpeg → jpg, ...).
