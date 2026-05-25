# ADR-0014: prod 프로파일 H2 in-memory → AWS RDS MySQL 전환 + HikariCP + Flyway 연기

- **Status**: Proposed
- **Date**: 2026-05-25
- **Deciders**: junseong kim
- **Trigger**: T1 (H2 영속 모드 vs MySQL/PostgreSQL/Aurora), T4 (RDS 인프라 신규 도입), T7 (Flyway 즉시 도입 연기 — ddl-auto: update 임시 채택)

## Context

Epic 1-3 (CD) 머지 직후 첫 EC2 배포 시점에 *application-prod.yml 의 datasource 가 H2 in-memory 우회 (`.env` 의 `SPRING_DATASOURCE_*` override)* 로 임시 운영. 한계:

- *데이터 영속 0* — 컨테이너 교체마다 모든 데이터 손실 (cd.yml 의 `docker stop/rm/run` 이 매 배포마다 실행)
- *prod 가 prod 답지 못함* — application-prod.yml 의 driver-class-name 은 MySQL 인데 실 DB 는 H2 → 운영 신뢰성 부재
- *.env 의 H2 우회 라인* (`SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver`, `SPRING_JPA_HIBERNATE_DDL_AUTO=update`) 가 yml 의 의도와 *조용히 충돌* — 운영 사고 원인 후보
- 향후 ALB+ASG (Product 2 후속) 도입 시 *공유 영속 DB 필수*

전제:
- AWS RDS Free Tier (12개월) 사용 가능 — 비용 0
- EC2 t3.micro + 같은 VPC 내 RDS db.t3.micro 가정
- 학습 단계 — *Multi-AZ / Read Replica / RDS Proxy 미적용*, *passwordless IAM 인증 미적용*

## Decision

**AWS RDS MySQL 8 + 환경변수 HOST/PORT/NAME 분리 + HikariCP 표준 옵션 + `ddl-auto: update` (1차) → 추후 Flyway 도입 시 `validate` 전환** 채택:

### application-prod.yml 핵심
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT:3306}/${DB_NAME}?useSSL=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      max-lifetime: 1700000          # < RDS wait_timeout (28800s)
      keepalive-time: 300000          # 5분
      idle-timeout: 600000
      validation-timeout: 5000
  jpa:
    hibernate:
      ddl-auto: update                # TODO: Flyway 도입 후 validate
    database-platform: org.hibernate.dialect.MySQLDialect
```

핵심 규칙:
- **URL 분리**: 단일 `${DB_URL}` 대신 `HOST/PORT/NAME` 분리 — endpoint 변경 (RDS rename / region migration) 시 단일 라인 갱신.
- **`useSSL=true` + `allowPublicKeyRetrieval=true`**: MySQL 8 의 caching_sha2_password 인증을 SSL 위에서 안전 수행. RDS 의 SSL endpoint 자동 사용.
- **`serverTimezone=Asia/Seoul` + `characterEncoding=UTF-8`**: JVM 의 KST + 한글 데이터 정합성.
- **`MySQLDialect` (versionless)**: Hibernate 7 권장 — connection 메타에서 *MySQL 버전 자동 감지*. 구 `MySQL8Dialect` 는 deprecated.
- **HikariCP 7종**: pool=10 (단일 t3.micro 적정), `max-lifetime=1700s` (RDS `wait_timeout=28800s` 보다 짧게 → stale 연결 자동 회수), `keepalive-time=5분` (private RDS 점검 시 NAT 게이트웨이 idle timeout 회피).
- **`ddl-auto: update`**: RDS 첫 도입 + 엔티티 자동 생성. Entity 추가 시 *컬럼/테이블 자동 ALTER*. 데이터 손실 위험 0 (DROP 미발생). `validate` 는 Flyway 도입 후 (별 Epic) 이행.

### build.gradle.kts 의존성 변경
- `runtimeOnly("com.mysql:mysql-connector-j")` — 그대로 (prod runtime).
- `runtimeOnly("com.h2database:h2")` → `developmentOnly` + `testRuntimeOnly` (prod jar 에서 H2 제거, bootRun/test 에서만 사용).
- `implementation("...spring-boot-h2console")` → `developmentOnly` (prod jar 에서 console 제거).

### EC2 .env 갱신 (사용자 작업)
신규 키: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
제거 키: `DB_URL`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME`, `SPRING_JPA_DATABASE_PLATFORM`, `SPRING_JPA_HIBERNATE_DDL_AUTO`

## Alternatives Considered

### Option A. H2 영속 모드 (file-based)
- **Pros**: 외부 의존성 0, 운영 비용 0.
- **Cons**: 단일 인스턴스 종속 — 컨테이너 교체 시 *볼륨 마운트 필수* + 멀티 인스턴스 (Product 2 ALB+ASG) 시 데이터 분기. 운영 신뢰성 부재.
- **기각 이유**: 영속 DB 의 *핵심 가치* (다중 인스턴스 공유 + 운영 백업 표준) 부재.

### Option B. PostgreSQL (Amazon RDS)
- **Pros**: 강한 타입 시스템 · JSON/JSONB 등 고급 기능.
- **Cons**: 학습 곡선 + 기존 코드/테스트 H2 MODE=MySQL 으로 작성 (MySQL 친화).
- **기각 이유**: 본 프로젝트의 *작성된 SQL 방언* 이 MySQL 친화. 마이그레이션 비용 > 이득.

### Option C. Amazon Aurora MySQL
- **Pros**: 자동 스케일/복제/장애 복구 · 성능.
- **Cons**: Free Tier 미적용 (t4g.medium 최소) — 학습 단계 비용 부담.
- **기각 이유**: 학습 단계 비용. 향후 트래픽 도달 시 마이그레이션 검토 (Aurora MySQL 호환 → 코드 변경 0).

### Option D. Flyway 즉시 도입 (ddl-auto: validate)
- **Pros**: 운영 표준 — schema 변경 *코드로 추적*, peer review, 롤백.
- **Cons**: 별 Epic 작업량 (V1__init.sql 작성 + 기존 schema reverse-engineer + CI 통합).
- **기각 이유**: T7. RDS 도입 자체 검증을 우선. Flyway 는 schema 안정화 후 별 Epic.

### Option E. JPA `create-drop` 또는 `create`
- **Pros**: 매 부팅 schema 초기화 — 깔끔.
- **Cons**: *데이터 손실*. 운영 부적합.
- **기각 이유**: 영속 DB 의도와 정면 충돌.

### Option F. AWS Secrets Manager / Parameter Store 자격증명
- **Pros**: 자격증명 회전 자동 / IAM 기반 감사.
- **Cons**: SDK 의존성 + 런타임 fetch 코드 + 추가 비용.
- **기각 이유**: T7. 현 단계 `.env` + GitHub Secrets 가 zero infra cost. 운영 성숙 후 마이그레이션.

### Option G. IAM 인증 (passwordless)
- **Pros**: 비밀번호 0 — 회전 불필요.
- **Cons**: RDS IAM auth 활성화 + 토큰 fetch 코드 + 토큰 15분 TTL → 풀 재연결 시 토큰 갱신.
- **기각 이유**: T7. 학습 단계 단순화 우선.

## Consequences

### Positive
- *데이터 영속* — 컨테이너 교체 시 데이터 유지.
- 다중 인스턴스 호환 기반 (Product 2 ALB+ASG 진입 시 *코드 변경 0*).
- HikariCP 표준 옵션으로 *RDS 점검/단절 자동 회수*.
- prod yml 이 *실 환경과 정합* — `.env` 의 H2 우회 라인 제거 가능.
- MySQL 8 + SSL/UTF-8/KST 표준 — 운영 일관성.

### Negative / Trade-offs
- *RDS 비용*: Free Tier 12개월 무료, 이후 db.t3.micro ~$15/월. 학습 종료 시 인스턴스 삭제 필요.
- *단일 AZ SPOF*: AZ 장애 시 RDS down. Multi-AZ 는 Free Tier 외.
- *Flyway 부재*: schema 변경이 *코드 base 에 흔적 없이* 자동 ALTER. 운영 시 *수동 추적 부담*. Follow-up 으로 도입 의무.
- *`useSSL=true` + 인증서 검증*: mysql-connector-j 8.x 기본 `verifyServerCertificate=true`. RDS CA cert 가 JVM cacerts 에 미포함 시 SSL handshake fail → 임시 `verifyServerCertificate=false` 추가 또는 RDS cert truststore 명시.
- *EC2 ↔ RDS 네트워크*: RDS SG 인바운드 3306 에 EC2 SG 허용 + 같은 VPC 필수. 별도 사용자 작업.
- *비밀번호 평문 EC2 .env*: chmod 600 으로 보호하나 운영 표준 (Secrets Manager) 대비 약함.

### Neutral
- ADR-0008 (profile 분리) / ADR-0004 (S3 fallback) / ADR-0010 (actuator `never`) 모두 *그대로 유지*.
- spring-boot-actuator 의 `DataSourceHealthIndicator` 가 자동 등록 → `/actuator/health` 가 *DB 연결 포함* 으로 평가 (UP/DOWN). details 는 ADR-0010 `never` 정책으로 비노출.
- 기존 테스트 (H2 in-memory) 그대로 유지 — `testRuntimeOnly` 에 H2 잔존.

## Follow-ups

- [ ] **Flyway 도입** + V1__init.sql + ddl-auto: validate 전환 — 별 Epic
- [ ] *RDS 인증서* trust chain 점검 — 실 배포 후 SSL handshake 실패 시 truststore 옵션 명시
- [ ] Multi-AZ 전환 — 가용성 요구 발생 시
- [ ] Read Replica + 읽기 분리 — 트래픽 도달 시
- [ ] RDS Proxy — 연결 수 폭증 시 (서버리스 / 다인스턴스)
- [ ] AWS Secrets Manager / Parameter Store 자격증명 — 회전 요구 시
- [ ] IAM 인증 (passwordless) — 보안 강화 시
- [ ] application-local.yml 의 H2 → docker-compose mysql 옵션 — local dev 가 prod 와 더 정합되어야 할 때
- [ ] CD 의 health gate 가 *DB UP 까지* 검증함을 README 에 1줄 명시
- *재검토 트리거*: RDS 비용 증가 / Free Tier 만료 / 트래픽 도달 / schema 변경 빈도 증가 / 보안 인시던트 / 운영 책임자 변경
