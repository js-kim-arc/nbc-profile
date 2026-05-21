# nbc-profile

클라우드 주차 과제입니다.

---

## 실행 (Profile 별)

본 프로젝트는 환경별 설정을 `application-{profile}.yml` 로 분리. 상세 결정은 [ADR-0008](docs/adr/0008-profile-based-yml-split.md) 참조.

### local (기본값, H2 in-memory)

```powershell
# profile 미지정 - application.yml 의 spring.profiles.default: local 활용
./gradlew.bat bootRun

# 또는 명시
./gradlew.bat bootRun --args="--spring.profiles.active=local"
```

- H2 콘솔: <http://localhost:8080/h2-console> (`jdbc:h2:mem:testdb`, user `sa`, password `sa`)
- 로그: `nbc.profile=DEBUG`, Hibernate SQL=DEBUG, `show-sql: true`
- S3: 공통 `application.yml` 의 디폴트 placeholder 사용 (`my-toy-bucket` / `ap-northeast-2`). 실제 호출 시점에 AWS 자격증명이 없으면 호출이 실패하지만, 컨텍스트 로딩 자체는 정상.

### test (단위/통합 테스트)

```powershell
./gradlew.bat test
```

- `InMemoryFileStorageAdapter` 활성 (S3 어댑터 비활성, `@Profile("!test")`).
- `ProfileSmokeTest` 가 두 profile (local + test) 의 컨텍스트 로딩 + 어댑터 주입 타입을 자동 검증.

### prod (운영 환경, 환경변수 필수)

`application-prod.yml` 은 모든 환경 의존 값을 *strict placeholder* (디폴트 없음) 로 둔다. 환경변수 누락 시 *부팅 실패* — 의도된 fail-fast.

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_URL = "jdbc:mysql://..."
$env:DB_USERNAME = "..."
$env:DB_PASSWORD = "..."
$env:S3_BUCKET = "..."
$env:S3_REGION = "..."
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

Bash:

```bash
SPRING_PROFILES_ACTIVE=prod \
  DB_URL=jdbc:mysql://... \
  DB_USERNAME=... \
  DB_PASSWORD=... \
  S3_BUCKET=... \
  S3_REGION=... \
  java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

S3 자격증명 (`S3_ACCESS_KEY` / `S3_SECRET_KEY`) 은 미설정 시 *DefaultCredentialsProvider* 가 fallback — IAM Role · `~/.aws/credentials` · AWS_* 환경변수 등에서 자동 탐색 ([ADR-0004](docs/adr/0004-s3-credentials-nullable-fallback.md)).

---

## 시크릿 컨벤션

- yml 파일에 시크릿 (DB password · S3 access key 등) *직접 기재 금지* — 모두 `${VAR}` placeholder.
- 로컬 개발 시 `.env` · IDE Run Configuration · `direnv` 등으로 주입.
- 운영은 systemd 서비스 파일 · AWS Parameter Store · Secrets Manager 등 외부 비밀 저장소가 placeholder 를 채움.

---

## 문서 진입

- 작업 흐름 · 규칙: `CLAUDE.md`
- 도메인 의도: `docs/DOMAIN.md`
- 패키지 · BC 구조: `docs/PACKAGE.md`
- 아키텍처 결정 기록: `docs/adr/index.md`
- 현재 Epic / Story: `workflow/epic/epic.md`, `workflow/story/story.md`


## 인프라 (Product 2: VPC + EC2)

### EC2 인스턴스 정보

| 항목 | 값 |
|---|---|
| Instance ID | `i-0abc1234...` |
| Public IPv4 | `3.36.121.211` |
| 인스턴스 타입 | t3.micro (Free Tier) |
| AMI | Amazon Linux 2023 (x86_64) |
| VPC / Subnet | `profile-vpc` / `profile-public-2a` (10.0.1.0/24) |
| Security Group | `profile-app-sg` (22/80/8080, My IP only) |
| 접속 URL (배포 후) | `http://3.36.121.211:8080` |

> **주의:** Elastic IP 미사용 결정(`ADR-NET-004`)으로, 인스턴스 stop/start 시 Public IP가 변경됩니다. 본 IP는 *최종 커밋 시점 기준*입니다.

### SSH 접속

```powershell
ssh -i "C:\study\study_related_important\profile-key.pem" ec2-user@3.36.121.211
```

### 본인 IP 변경 시 SG 갱신 절차

SSH가 `Connection timed out`으로 실패하면, 보안 그룹 인바운드의 등록된 IP가 *현재 본인 IP와 다른 상태*입니다 (Wi-Fi 전환·모바일 핫스팟·VPN 변경 등). 아래 절차로 5분 내 복구.

1. 현재 본인 IP 확인
```powershell
   curl https://checkip.amazonaws.com
```
2. AWS Console → EC2 → Security Groups → `profile-app-sg`
3. Inbound rules 탭 → Edit inbound rules
4. 22 / 80 / 8080 세 룰의 Source를 `<NEW_IP>/32`로 갱신 (Source 드롭다운 → **My IP** 선택하면 자동 입력)
5. Save rules → SSH 재시도

### 인스턴스 재시작 시 주의

- 인스턴스 stop → start 시 Public IP가 변경됨 (Elastic IP 미사용)
- 재시작 후 README의 Public IP를 *수동 갱신*해야 함
- 학습 중에는 *인스턴스를 의도적으로 중지하지 않는 것*이 운영 절차 (Free Tier 750시간/월 = 24/7 가능)

### 인프라 다이어그램

\`\`\`
[My PC]
│ SSH (22) / HTTP (80) / 8080
│ My IP /32 only
▼
┌──────────────────────────────────────────┐
│ profile-vpc (10.0.0.0/16)                │
│  ┌─────────────────────────────────────┐ │
│  │ Public Subnet 10.0.1.0/24 (2a)      │ │
│  │   ┌──────────────────────┐          │ │
│  │   │ EC2 profile-app-01   │          │ │
│  │   │  t3.micro, AL2023    │          │ │
│  │   └──────────────────────┘          │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │ Public Subnet 10.0.2.0/24 (2c)      │ │
│  │  (empty - reserved for ALB in P7)   │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │ Private Subnet 10.0.11.0/24 (2a)    │ │
│  │  (empty - reserved for RDS in P7)   │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │ Private Subnet 10.0.12.0/24 (2c)    │ │
│  │  (empty - reserved for RDS in P7)   │ │
│  └─────────────────────────────────────┘ │
│  Internet Gateway: profile-igw           │
└──────────────────────────────────────────┘
\`\`\`