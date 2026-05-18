# ADR-0004: S3 자격증명 nested record 통째 nullable + DefaultCredentialsProvider fallback

- **Status**: Accepted
- **Date**: 2026-05-18
- **Deciders**: junseong kim

## Context

AWS S3 자격증명은 환경에 따라 *전혀 다른 형태* 로 주입:
- **IAM Role** (EC2 / ECS / Lambda 운영): access-key · secret-key *자체가 없어야 정상*. 인스턴스 메타데이터 / role assume 으로 자동 획득.
- **Static** (로컬·LocalStack·CI): access-key · secret-key 명시 주입.
- **`~/.aws/credentials` 파일** (로컬 개발자 머신): 환경변수 없이 파일에서.

이 셋을 *한 ConfigurationProperties 구조* 에서 표현해야. 모든 필드에 `@NotBlank` 박으면 IAM Role 환경 부팅 실패. 자격증명을 *완전히 분리된 자체 Bean* 으로 빼면 ADR-0003 (응집도) 위배.

## Decision

**`Credentials` nested record 를 통째 nullable, 내부 필드도 무검증**. `isStatic()` 헬퍼로 *둘 다 blank 아닐 때만* `StaticCredentialsProvider` 사용, 그 외 (`Credentials == null` OR `isStatic() == false`) 는 AWS SDK `DefaultCredentialsProvider` 로 fallback.

```java
public record Credentials(String accessKey, String secretKey) {
    public boolean isStatic() {
        return accessKey != null && !accessKey.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }
}
```

`@Valid Credentials credentials` 는 record 가 *존재할 때만* 내부 검증을 트리거 — null 이면 검증 안 함.

## Alternatives Considered

### Option A: `@NotBlank String accessKey, @NotBlank String secretKey`

- **Pros**: 부팅 시점 자격증명 누락 명시 fail-fast.
- **Cons**: IAM Role 환경에서 *정상 케이스* 가 부팅 실패. 운영 환경에서 사용 불가.
- **기각 이유**: IAM Role 환경 미지원.

### Option B: nested record nullable + `isStatic()` (채택)

- **Pros**: IAM Role · Static · `~/.aws/credentials` 모두 한 구조에서. 운영 시크릿 외부화 자연.
- **Cons**: *부분 자격증명* (access-key 만 있고 secret-key 없는) 케이스가 `isStatic() == false` 로 *조용히* `DefaultCredentialsProvider` fallback → 잘못된 자격증명으로 운영 호출 시 *런타임 NoSuchKey/AccessDenied* 발견.
- **기각 사유**: 없음 — 채택. 단점은 *경고 로그* (Follow-up) 로 완화.

### Option C: `@Profile("aws") @Profile("localstack")` 별도 Credentials Config

- **Pros**: profile 별 명시.
- **Cons**: ADR-0001 (env-based 분기, profile 회피) 와 모순. 새 환경마다 새 Config.
- **기각 이유**: ADR-0001 위배.

### Option D: 자격증명을 *완전히 분리된 자체 Bean* (`AwsCredentialsProvider`) 으로 추출

- **Pros**: S3 Properties 가 자격증명을 모름.
- **Cons**: S3 Config 가 *다른 Config 의 Bean* 을 의존 — ADR-0003 응집도 원칙 위배.
- **기각 이유**: 응집도 손상.

## Consequences

### Positive

- IAM Role · Static · `~/.aws/credentials` 모두 *한 구조* 에서 처리.
- 운영 시크릿이 yml 에서 빠짐 (`access-key:`, `secret-key:` 비워둠 → 환경변수 → 미주입 → null → fallback).
- 같은 패턴이 향후 다른 외부 서비스 (Redis 인증, 메시징) 도입 시 *applicable*.

### Negative / Trade-offs

- **부분 자격증명 조용한 fallback**: access-key 만 설정·secret-key 누락 케이스가 `isStatic() == false` 처리되어 `DefaultCredentialsProvider` 시도. 잘못된 자격증명을 *부팅 시점에 알아채지 못함*.
- **`DefaultCredentialsProvider` 체인 의존**: 환경변수 → 시스템 프로퍼티 → 컨테이너 자격증명 → 인스턴스 메타데이터 순서. *어디서 자격증명이 왔는지* 가 *암묵적*.
- **로컬 개발자가 잘못된 자격증명으로 운영 버킷 접근 위험**: AWS 환경변수가 설정된 머신에서 *로컬 개발 시* fallback 으로 운영 자격증명 사용. 정책으로 막아야.

### Neutral

- `DefaultCredentialsProvider` 가 *lazy 초기화* 라 Bean 등록 자체는 자격증명 부재여도 통과 — 테스트 컨텍스트 로드 안정.

## Follow-ups

- [ ] `S3StorageConfig.resolveCredentials()` 에서 `isStatic() == false` 인데 *한쪽만 비어있는* 케이스 *WARN 로그* 추가 (별도 PR).
- [ ] `DefaultCredentialsProvider` 체인 (환경변수 → `~/.aws/credentials` → IAM Role) 동작 README 안내 (별도 PR).
- [ ] *재검토 트리거*: 운영 사고로 *잘못된 자격증명 fallback* 사례 1 건 이상 발생 시 본 결정 재평가 — `@AssertTrue` 로 *둘 다 있거나 둘 다 없거나* 의무화 검토.
- [ ] *재검토 트리거*: 외부 서비스 (Redis · 메시징) 도입으로 *동일 자격증명 패턴* 적용 필요 시, 본 ADR 을 *일반화* (예: `CredentialsConfig` 공통 record).
