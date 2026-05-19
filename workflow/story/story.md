## Story 2-1. 로그 표준 포맷 + ExceptionHandler 로그 + Actuator Health 화이트리스트

### User Story

> As a 백엔드 개발자,
I want 모든 API 요청에 표준 INFO 로그가 남고, 예외는 *예상/예상치 못함*으로 분리되며, `/actuator/health`만 노출되길,
So that EC2 배포 후 `journalctl`로 *요청 흐름 추적*이 가능하고, ALB / Monitoring이 *지금 박힌 baseline*을 그대로 활용한다.
>

### 설계 노트

- **`build.gradle.kts` 의존성 추가**

    ```kotlin
    dependencies {
        // ... 기존 의존성
        implementation("org.springframework.boot:spring-boot-starter-actuator")
    }
    ```

- **`application.yml` Actuator 설정 (공통)**

    ```yaml
    management:
      endpoints:
        web:
          exposure:
            include: health
            exclude: env, beans, heapdump, threaddump, configprops, mappings
          base-path: /actuator
      endpoint:
        health:
          show-details: never
          probes:
            enabled: false      # K8s liveness/readiness 분리는 v2
      server:
        port: 8080              # 비즈니스 포트와 통합 (분리는 v2)
    ```

- **`RequestLoggingFilter.java`** (`nbc.profile.common.web.logging`)

    ```java
    package nbc.profile.common.web.logging;
    
    import jakarta.servlet.FilterChain;
    import jakarta.servlet.ServletException;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.stereotype.Component;
    import org.springframework.web.filter.OncePerRequestFilter;
    
    import java.io.IOException;
    
    @Slf4j
    @Component
    public class RequestLoggingFilter extends OncePerRequestFilter {
    
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            // Actuator 경로 제외 — 헬스체크 폴링이 로그 노이즈 되지 않도록
            return uri.startsWith("/actuator") || uri.startsWith("/h2-console");
        }
    
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
    
            long start = System.currentTimeMillis();
            String method = request.getMethod();
            String uri = request.getRequestURI();
    
            log.info("[API - LOG] {} {} START", method, uri);
    
            try {
                chain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - start;
                int status = response.getStatus();
                log.info("[API - LOG] {} {} {} ({}ms)", method, uri, status, duration);
            }
        }
    }
    ```

- **`GlobalExceptionHandler.java` 업데이트** (`nbc.profile.common.exception`)

    > 본 프로젝트는 `BusinessException + ErrorCode(HttpStatus)` 통합 처리. 로그 레벨은 *status 기반 자동* (ADR-0009) — 새 예외 / ErrorCode 추가 시 핸들러 무수정.

    ```java
    @Slf4j
    @RestControllerAdvice
    public class GlobalExceptionHandler {
    
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
            ErrorCode code = ex.getErrorCode();
            HttpStatus status = code.getStatus();
            if (status.is5xxServerError()) {
                log.error("[API - LOG] {} {}", code.name(), code.getMessage(), ex);
            } else {
                log.warn("[API - LOG] {} {}", code.name(), code.getMessage());
            }
            return ResponseEntity.status(status).body(ApiResponse.error(code));
        }
    
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<List<String>>> handleValidation(MethodArgumentNotValidException ex) {
            List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                    .map(f -> "%s: %s".formatted(f.getField(), f.getDefaultMessage()))
                    .toList();
            log.warn("[API - LOG] VALIDATION_FAILED {}", errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, errors));
        }
    
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
            log.warn("[API - LOG] FILE_TOO_LARGE {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE));
        }
    
        @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
        public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
            log.warn("[API - LOG] RESOURCE_NOT_FOUND {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
        }
    
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
            log.error("[API - LOG] INTERNAL_ERROR", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("INTERNAL_ERROR", "unexpected error", null));
        }
    }
    ```
    
    > 차이점 정리:
    > - 본 프로젝트는 *MemberNotFoundException · ProfileImageNotFoundException* 같은 *개별 예외 클래스* 가 모두 `BusinessException` 상속 — `handleBusiness` 단일 핸들러로 통합.
    > - `ImageStorageException` 은 본 프로젝트에 미존재 (`FileStorageException` 으로 명명) — 역시 `BusinessException` 상속이라 별도 핸들러 불필요.
    > - `RESOURCE_NOT_FOUND` ErrorCode 추가 + `handleNotFound` — Actuator exclude 된 endpoint 의 404 응답을 위해 본 Story 에서 추가.
    > - `@RestControllerAdvice` basePackages 제한 없음 — 본 프로젝트는 단일 BC.

- **`logback-spring.xml` (선택 — 콘솔 패턴 표준화)**

    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
        <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
        <property name="LOG_PATTERN"
                  value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"/>
    
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>${LOG_PATTERN}</pattern>
            </encoder>
        </appender>
    
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </configuration>
    ```

- **예상 로그 출력 샘플**

    ```
    2026-05-19 14:23:01.234 INFO  [http-nio-8080-exec-1] n.p.c.w.l.RequestLoggingFilter - [API - LOG] POST /api/members START
    2026-05-19 14:23:01.298 INFO  [http-nio-8080-exec-1] n.p.c.w.l.RequestLoggingFilter - [API - LOG] POST /api/members 201 (64ms)
    
    2026-05-19 14:23:05.111 INFO  [http-nio-8080-exec-2] n.p.c.w.l.RequestLoggingFilter - [API - LOG] GET /api/members/999 START
    2026-05-19 14:23:05.115 WARN  [http-nio-8080-exec-2] n.p.c.e.GlobalExceptionHandler  - [API - LOG] MEMBER_NOT_FOUND member not found
    2026-05-19 14:23:05.117 INFO  [http-nio-8080-exec-2] n.p.c.w.l.RequestLoggingFilter - [API - LOG] GET /api/members/999 404 (6ms)
    ```


### 완료 기준 (Acceptance Criteria)

- [ ]  `RequestLoggingFilter`가 `@Component`로 자동 등록되어 모든 요청 1회당 *2건 로그* (START + 완료)
- [ ]  로그 메시지가 `[API - LOG] {METHOD} {URI} {STATUS} ({DURATION}ms)` 포맷 준수
- [ ]  `/actuator/*` 호출 시 `RequestLoggingFilter` 로그 *발생 안 함*
- [ ]  `MemberNotFoundException` 발생 시 WARN 로그, stack trace *없음*
- [ ]  `Exception` catch-all 시 ERROR 로그, stack trace *포함*
- [ ]  `curl /actuator/health` → 200 + `{"status":"UP"}`
- [ ]  `curl /actuator/env` → 404
- [ ]  `curl /actuator/heapdump` → 404
- [ ]  로컬 profile에서 `nbc.profile` 패키지 DEBUG 로그 출력 (Story-001-1 의 application-local.yml 적용분), prod profile에서 INFO 이상만
- [ ]  `RequestLoggingFilterTest` 그린 (MockMvc로 검증)

### 엣지 케이스

- 요청 처리 중 *Filter 이전*에 예외 (예: Tomcat이 *Content-Type 파싱 실패*) → `RequestLoggingFilter`의 START 로그만 남고 *완료 로그 없음* — `finally` 블록이 잡지 못함. 무관 — Tomcat *Access Log*가 별도로 잡음 (v2에서 활성화)
- 요청이 *오래 걸리는 중* SSH로 EC2에 접속해 `journalctl -u thirdtool-app -f` → START 로그만 *5초 이상 떠 있음* → *진행 중 요청* 식별 가능 (의도된 동작)
- 동일 요청에 대해 *INFO 2건* — Monitoring Product의 *RPS 메트릭*과 카운트가 *2배* 되지 않는지 확인 필요 (Filter는 1회 호출, 로그만 2건이므로 무관)
- `/h2-console/*` 호출이 *로그 노이즈* 일으킬 가능성 → `shouldNotFilter`에 포함 (위 코드 반영)
- ERROR 로그의 stack trace가 *수십 줄* — 운영에서 *CloudWatch Logs* 비용 영향 가능. 본 Product는 *journalctl 콘솔만* → 비용 영향 0. v2(CloudWatch Logs)에서 *ERROR만 별도 그룹*으로 분리
- Actuator 의존성 추가만으로 *기본 메트릭 등록* (Micrometer 자동 설정) — `/actuator/prometheus`는 *exclude되어 노출 안 됨*. Monitoring Product에서 *include에 추가*하는 1줄 변경

### Definition of Done

- [ ]  코드 리뷰 완료
- [ ]  로컬 요청 시 표준 로그 포맷 출력 캡처 (`curl localhost:8080/api/members ...`)
- [ ]  WARN/ERROR 분리 검증 (정상 + 404 + 500 시나리오 로그 캡처)
- [ ]  `curl /actuator/health` 응답 캡처
- [ ]  민감 endpoint 404 응답 캡처 (`/env`, `/heapdump`, `/beans`)
- [ ]  `RequestLoggingFilterTest` + `ActuatorEndpointTest` 그린

### 의존성

- 선행: Story 1-1 (profile별 `logging.level.*` 설정, 머지 완료), Member Epic (GlobalExceptionHandler 존재)
- 후속: 배포 Epic (운영 환경에서 *동일한 로그*가 `journalctl` 로 보임을 검증), 향후 Monitoring Epic (`prometheus` 를 include 에 *한 줄 추가*)

### 스토리 포인트

- 추정: 3 SP