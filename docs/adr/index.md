# ADR Index

> 새 ADR 작성 전 `.claude/rules/adr.md` 의 7 개 트리거 + 안티패턴 표 확인. 본문은 `docs/adr/template.md` 사용. 번호는 max+1 (4자리), Status 는 **Proposed** 로 시작.

| # | 제목 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [ADR-0001](0001-env-based-config-strategy.md) | 환경변수 기반 설정 분기 채택, profile yml 분리 회피 | Accepted | 2026-05-18 |
| [ADR-0002](0002-validation-starter-for-config.md) | spring-boot-starter-validation 으로 config startup-time 검증 | Accepted | 2026-05-18 |
| [ADR-0003](0003-explicit-enable-configuration-properties.md) | `@EnableConfigurationProperties` 명시 등록 채택 | Accepted | 2026-05-18 |
| [ADR-0004](0004-s3-credentials-nullable-fallback.md) | S3 credentials nested record nullable + DefaultCredentialsProvider fallback | Accepted | 2026-05-18 |
| [ADR-0005](0005-api-response-envelope-and-errorcode-mapping.md) | ApiResponse envelope 통일 + ErrorCode 매핑 정책 + URL prefix `/api/members` | Accepted | 2026-05-18 |
| [ADR-0006](0006-multipart-isolation-with-image-upload-command.md) | Multipart 격리 — Controller-only 경계, `ImageUploadCommand` record | Accepted | 2026-05-18 |
| [ADR-0007](0007-storage-key-format-policy.md) | Storage key 생성 정책 — `profile/{memberId}/{uuid}.{ext}` | Accepted | 2026-05-18 |
