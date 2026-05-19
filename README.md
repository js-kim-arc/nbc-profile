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
