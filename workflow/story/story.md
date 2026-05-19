## Story 1-1. `application-{profile}.yml` 3분할 + Profile별 컨텍스트 로딩 검증

### User Story

> As a 백엔드 개발자,
I want 설정 파일이 *공통 / local / prod* 3개로 분리되고 *active profile 설정만으로* 환경이 전환되길,
So that 로컬 개발은 H2로 가볍게, 운영 배포는 MySQL/Parameter Store로 안전하게 진행되고, 환경 간 사고가 *구조적으로 차단*된다.
>

### 설계 노트

- **`src/main/resources/application.yml` (공통)**

    ```yaml
    spring:
      application:
        name: demo
      profiles:
        default: local
      jpa:
        hibernate:
          ddl-auto: update
        properties:
          hibernate:
            format_sql: true
            jdbc:
              time_zone: Asia/Seoul
      servlet:
        multipart:
          max-file-size: 5MB
          max-request-size: 5MB
      jackson:
        time-zone: Asia/Seoul
        date-format: yyyy-MM-dd'T'HH:mm:ss
    
    app:
      storage:
        s3:
          bucket: ${S3_BUCKET:my-toy-bucket}
          region: ${S3_REGION:ap-northeast-2}
          endpoint: ${S3_ENDPOINT:}
          credentials:
            access-key: ${S3_ACCESS_KEY:}
            secret-key: ${S3_SECRET_KEY:}
          presigned:
            default-expiration-seconds: ${S3_PRESIGNED_TTL_SECONDS:300}
    
    # 메모: app.version: @project.version@ 같은 Gradle resource filtering 항목은
    # 본 Story 범위 외 — 별도 후속 작업으로.
    ```

- **`src/main/resources/application-local.yml` (H2)**

    ```yaml
    spring:
      datasource:
        url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE
        driver-class-name: org.h2.Driver
        username: sa
        password: sa
      jpa:
        show-sql: true
      h2:
        console:
          enabled: true
          path: /h2-console
    
    logging:
      level:
        root: INFO
        nbc.profile: DEBUG
        org.hibernate.SQL: DEBUG
    ```
    
    > 메모: 본 프로젝트는 *S3 일원화* (LocalFileSystem 어댑터 미존재). local 도 S3FileStorageAdapter 활성 — `app.storage.s3.*` 디폴트 placeholder 가 공통 application.yml 에 있어 별도 override 불필요.

- **`src/main/resources/application-prod.yml` (MySQL placeholder strict + S3 strict + prod 로그)**

    ```yaml
    spring:
      datasource:
        url: ${DB_URL}                  # 디폴트 제거 — 환경변수 미설정 시 부팅 실패
        driver-class-name: com.mysql.cj.jdbc.Driver
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
      jpa:
        hibernate:
          ddl-auto: validate            # 운영 자동 스키마 변경 차단
        show-sql: false                 # prod는 SQL 로그 끔 (Monitoring 메트릭으로 대체)
    
    app:
      storage:
        s3:
          bucket: ${S3_BUCKET}          # 디폴트 제거 — strict
          region: ${S3_REGION}          # 디폴트 제거 — strict
          # endpoint / credentials 는 공통 application.yml 의 placeholder 유지
          # (ADR-0004 DefaultCredentialsProvider fallback 활용)
    
    logging:
      level:
        root: INFO
        nbc.profile: INFO
        org.hibernate.SQL: WARN
    ```

- **`build.gradle.kts` 의존성 (MySQL 드라이버) — *이미 추가됨***

    ```kotlin
    dependencies {
        // ... 기존 의존성
        runtimeOnly("com.mysql:mysql-connector-j")   // prod profile에서 사용
    }
    ```
    
    > 본 프로젝트는 *Kotlin DSL 빌드* (`build.gradle.kts`). MySQL 드라이버는 이미 등록된 상태 — 본 Story 의 의존성 변경 *없음*.

- **Profile 활성화 방법 (배포 Epic 에서 운영 환경에 적용)**

    ```bash
    # 로컬 — default(=local), spring.profiles.default: local 활용
    ./gradlew bootRun
    
    # 로컬 — local 명시
    ./gradlew bootRun --args='--spring.profiles.active=local'
    
    # 운영 — prod (환경변수 방식)
    SPRING_PROFILES_ACTIVE=prod \
      DB_URL=jdbc:mysql://... \
      DB_USERNAME=... \
      DB_PASSWORD=... \
      S3_BUCKET=... \
      S3_REGION=... \
      java -jar app.jar
    ```
    
    > PowerShell 환경에서는 `$env:SPRING_PROFILES_ACTIVE="prod"; ./gradlew.bat bootRun` 형태.

- **`ProfileSmokeTest.java`**

    ```java
    class ProfileSmokeTest {
    
        @Nested
        @SpringBootTest
        @ActiveProfiles("local")
        class LocalProfile {
            @Autowired FileStoragePort fileStoragePort;
    
            @Test
            void contextLoads_localProfile_injectsS3Adapter() {
                assertThat(fileStoragePort).isInstanceOf(S3FileStorageAdapter.class);
            }
        }
    
        @Nested
        @SpringBootTest
        @ActiveProfiles("test")
        class TestProfile {
            @Autowired FileStoragePort fileStoragePort;
    
            @Test
            void contextLoads_testProfile_injectsInMemoryAdapter() {
                assertThat(fileStoragePort).isInstanceOf(InMemoryFileStorageAdapter.class);
            }
        }
    
        // prod profile은 환경변수 의존 (strict placeholder) 이라 단위 테스트 제외
        // — 향후 통합/배포 검증 단계로 위임
    }
    ```


### 완료 기준 (Acceptance Criteria)

- [ ]  `src/main/resources/`에 3개 yaml 파일 존재 (`application.yml`, `local.yml`, `prod.yml`)
- [ ]  공통 설정이 `application.yml`에만, 환경별 설정이 *해당 profile 파일에만* 위치 (중복 없음)
- [ ]  `./gradlew bootRun` 실행 시 *default profile 활성*되며 H2 콘솔 접근 가능
- [ ]  `./gradlew bootRun --args='--spring.profiles.active=local'` 결과 동일
- [ ]  `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun` 시 *DB_URL 미설정* 에러 발생 (placeholder가 실제 작동)
- [ ]  `ProfileSmokeTest`가 `local`/`test` 두 profile에서 ApplicationContext 정상 로딩
- [ ]  본 프로젝트의 `S3FileStorageAdapter`가 default/local에서, `InMemoryFileStorageAdapter`가 test에서 *각각 주입* 확인

### 엣지 케이스

- 환경변수 미설정 상태로 prod 부팅 → Spring은 `${DB_URL}` placeholder를 *그대로 문자열*로 사용해 *드라이버 연결 시점*에 에러. *부팅 후 늦은 실패*보다 *부팅 시점 실패*가 안전 → `spring.config.import` 또는 `@Value` 검증으로 *조기 실패* 유도
- `application.yml` *공통 설정*에 `spring.datasource.url`이 *실수 포함* → local/prod에서 override되지만 *의도 불명* → 공통 파일에 DataSource 항목 *금지* 컨벤션 명시
- profile 이름 *오타* (`prdo`) → Spring이 *해당 yaml을 찾지 못함* → 부팅은 성공하지만 *application.yml만 사용* → 운영에서 *H2가 뜨는 사고*. 방지: systemd 서비스 파일에 *고정 문자열* 박기 (사람 입력 배제)
- IDE에서 *Run Configuration*에 active profile 설정 안 함 → 매번 추가 인자 입력. IntelliJ의 `application.yml` 우측 상단 *profile 드롭다운* 사용 권장
- Gradle `bootRun` 태스크에 `-args` 인자 전달 시 *큰따옴표* 누락하면 인자 분리 오류 → `bash` 환경에서 항상 `--args='...'`, PowerShell 에선 `--args=\"...\"` 형태
- *Spring Boot의 profile-yml 우선순위*: 명시 active profile yml > default profile yml > application.yml. profile yml 에 동일 키 명시 시 *override*, 미명시 시 *공통 값 상속*.

### Definition of Done

- [ ]  코드 리뷰 완료
- [ ]  3개 yaml 파일 *diff* 캡처 (공통 vs local vs prod 차이 명확)
- [ ]  `ProfileSmokeTest` 그린
- [ ]  로컬 H2 콘솔 접속 화면 + prod 부팅 *실패 메시지* 캡처 (placeholder 검증)
- [ ]  README에 *profile별 실행 명령* 섹션 추가

### 의존성