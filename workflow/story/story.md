
### **Story 1-1-1. 멀티스테이지 Dockerfile 작성**

```smalltalk
## Story 1-1-1. 멀티스테이지 Dockerfile 작성

### User Story
> As a **ThirdTool 백엔드를 컨테이너로 배포하려는 개발자**,
> I want **빌드 단계와 실행 단계가 분리된 Dockerfile을 갖길**,
> So that **빌드 도구가 빠진 가벼운 이미지로 EC2에서 빠르게 기동할 수 있다**.

### 설명
- 1단계(builder): eclipse-temurin:21-jdk 기반 + Gradle Wrapper로 fat JAR 빌드
- 2단계(runtime): eclipse-temurin:21-jre 기반 + builder에서 JAR만 복사
- 실행 명령: `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`
- 빌드 캐시 최적화: `build.gradle`, `settings.gradle`을 먼저 복사하여 의존성 레이어 캐싱

### 완료 기준 (Acceptance Criteria)
- [ ] 프로젝트 루트에 Dockerfile이 존재하고 FROM이 2개(builder/runtime)로 구성되어 있다
- [ ] builder 단계에서 `./gradlew bootJar`가 실행되고 산출물이 runtime 단계로 COPY된다
- [ ] runtime 이미지에는 JDK와 Gradle 캐시가 포함되지 않는다 (`docker history`로 확인 가능)
- [ ] 의존성만 변경되지 않았을 때 재빌드 시 의존성 레이어가 캐시에서 재사용된다
- [ ] 엣지 케이스: `.dockerignore`에 `build/`, `.gradle/`, `.git/`, `*.md`가 포함되어 컨텍스트 크기가 최소화된다

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] Dockerfile 빌드 성공 확인
- [ ] ADR 작성 완료 (멀티스테이지 빌드 선택 이유)
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 5 SP
```

### **Story 1-1-2. 로컬 컨테이너 기동 및 이미지 경량화 검증**

```smalltalk
## Story 1-1-2. 로컬 컨테이너 기동 및 이미지 경량화 검증

### User Story
> As a **컨테이너화 작업을 검증하려는 개발자**,
> I want **빌드된 이미지가 로컬에서 정상 기동하고 크기가 충분히 작음을 확인하길**,
> So that **EC2 배포 전 환경 의존성·이미지 크기 리스크를 모두 제거할 수 있다**.

### 설명
- 로컬 `docker run`으로 컨테이너 기동 → `/actuator/health` 200 응답 확인
- 단일 스테이지 빌드 이미지와 멀티스테이지 빌드 이미지 크기 비교 측정
- 측정 결과를 ADR 또는 포트폴리오 산출물로 정리

### 완료 기준 (Acceptance Criteria)
- [ ] `docker run -p 8080:8080 thirdtool:local` 실행 후 애플리케이션이 정상 기동된다
- [ ] `curl localhost:8080/actuator/health` 응답이 `{"status":"UP"}`이다
- [ ] `docker images`로 확인한 최종 이미지 크기가 단일 스테이지 대비 명확히 감소했다 (수치 기록)
- [ ] application.yml의 환경 변수(DB URL 등)가 컨테이너 실행 시 주입 가능하다
- [ ] 엣지 케이스: DB 미연결 상태에서도 컨테이너 기동 자체는 실패하지 않고, Health Check에서만 실패로 표시된다

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] 이미지 크기 비교 결과 README/ADR 기록 완료
- [ ] 로컬 기동 스크린샷 산출물 저장
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 3 SP
```
