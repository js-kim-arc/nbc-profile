### **Story 1-2-1. main push / PR 트리거 워크플로우 구성**

```smalltalk
## Story 1-2-1. main push / PR 트리거 워크플로우 구성

### User Story
> As a **코드 변경을 자동 검증받고 싶은 개발자**,
> I want **main으로의 push와 PR 생성 시 자동으로 워크플로우가 실행되길**,
> So that **수동으로 빌드/테스트를 돌리지 않아도 코드 안정성이 보장된다**.

### 설명
- `.github/workflows/ci.yml` 생성
- 트리거: `on: push (main)` + `on: pull_request (main)`
- 러너: `ubuntu-latest`
- Job 단위로 명확히 분리: `build-and-test` Job 우선 구성

### 완료 기준 (Acceptance Criteria)
- [ ] `.github/workflows/ci.yml`이 main push 시 자동 실행된다
- [ ] feature 브랜치에서 main으로 PR 생성 시 동일 워크플로우가 실행된다
- [ ] 워크플로우 실행 결과(성공/실패)가 GitHub PR 화면에 체크로 표시된다
- [ ] 실행 로그에 각 스텝(checkout, setup-java, build, test)이 명확히 구분되어 있다
- [ ] 엣지 케이스: docs/ 또는 README 단독 변경 시에도 워크플로우는 실행됨 (단순화 우선, paths 필터는 v2)

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] main 푸시 1회 실행 후 성공 확인
- [ ] PR 테스트 시나리오 1회 실행 후 성공 확인
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 3 SP
```

### **Story 1-2-2. Gradle 빌드/테스트 실행 및 실패 시 차단**

```smalltalk
## Story 1-2-2. Gradle 빌드/테스트 실행 및 실패 시 차단

### User Story
> As a **머지 전 안전망을 원하는 개발자**,
> I want **CI에서 Gradle 빌드와 테스트가 모두 통과해야 다음 단계로 갈 수 있길**,
> So that **테스트가 깨진 채로 배포 파이프라인이 진행되는 사고를 막을 수 있다**.

### 설명
- `./gradlew clean build` 실행 (테스트 포함)
- Gradle 의존성 캐시 적용 (`actions/cache` 또는 `gradle/gradle-build-action`)
- 테스트 실패 시 워크플로우 전체가 실패로 종료
- 테스트 리포트(`build/reports/tests`)를 Artifact로 업로드

### 완료 기준 (Acceptance Criteria)
- [ ] `./gradlew clean build`가 워크플로우 내에서 성공적으로 실행된다
- [ ] 1개라도 테스트가 실패하면 워크플로우 전체가 실패 상태로 종료된다
- [ ] Gradle 캐시 적용 후 2회차 빌드부터 빌드 시간이 1회차 대비 명확히 감소한다
- [ ] 테스트 실패 시 Actions UI에서 실패 테스트 클래스와 라인이 확인 가능하다
- [ ] 빌드 산출물 JAR이 Artifact로 업로드되어 다운로드 가능하다
- [ ] 엣지 케이스: 일시적 의존성 다운로드 실패 시 워크플로우는 실패로 표시되고 재실행으로 복구 가능

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] 의도적으로 실패하는 테스트로 차단 동작 검증 1회
- [ ] 캐시 적용 전후 빌드 시간 비교 기록
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 5 SP
```

---

### **Story 1-3-1. 이미지 태깅 전략 및 Docker Hub 자동 Push**

```smalltalk
## Story 1-3-1. 이미지 태깅 전략 및 Docker Hub 자동 Push

### User Story
> As a **배포된 버전을 추적하고 싶은 개발자**,
> I want **CI 성공 시 이미지가 커밋 SHA 태그와 latest 태그로 함께 Docker Hub에 Push되길**,
> So that **현재 배포 중인 정확한 버전을 식별할 수 있고 필요 시 특정 SHA로 롤백할 수 있다**.

### 설명
- CI 워크플로우 뒤에 `build-and-push` Job 추가 또는 동일 Job 연장
- `docker/login-action` + `docker/build-push-action` 활용
- 태그: `${DOCKERHUB_USERNAME}/thirdtool:${{ github.sha }}` + `:latest`
- buildx 활용한 캐시 최적화

### 완료 기준 (Acceptance Criteria)
- [ ] main push로 CI 성공 시 Docker Hub에 두 개 태그(`<sha>`, `latest`)로 이미지가 Push된다
- [ ] Docker Hub Repository 페이지에서 새 태그가 확인 가능하다
- [ ] PR에서는 이미지 Push가 실행되지 않는다 (main push에서만)
- [ ] Push 단계에서 Docker Hub 자격 증명이 로그에 노출되지 않는다
- [ ] 엣지 케이스: 동일 SHA로 재실행 시 기존 태그를 덮어쓰는 동작이 의도된 것으로 문서화

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] Docker Hub Push 성공 스크린샷 산출물 저장
- [ ] Secrets 설정 가이드를 README에 기록
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 5 SP
```

### **Story 1-3-2. EC2 SSH 자동 접속 및 docker pull/run 스크립트**

```smalltalk
## Story 1-3-2. EC2 SSH 자동 접속 및 docker pull/run 스크립트

### User Story
> As a **배포를 자동화하고 싶은 개발자**,
> I want **이미지 Push 직후 EC2가 자동으로 새 이미지를 받아 컨테이너를 교체하길**,
> So that **수동 SSH 접속 없이 코드 푸시 한 번으로 운영 환경이 갱신된다**.

### 설명
- `appleboy/ssh-action`으로 EC2에 SSH 접속
- 원격에서 실행할 스크립트: `docker pull <image>:latest` → 기존 컨테이너 stop/rm → 새 컨테이너 run
- 환경 변수 주입은 EC2 측 `.env` 파일 또는 `docker run -e`로 처리
- 컨테이너 이름 고정(`--name thirdtool`)으로 후속 교체 단순화

### 완료 기준 (Acceptance Criteria)
- [ ] CI에서 이미지 Push 완료 후 deploy Job이 EC2에 SSH로 접속 성공한다
- [ ] EC2에서 `docker pull` → 기존 컨테이너 중지/제거 → 새 컨테이너 실행이 순차적으로 수행된다
- [ ] 배포 직후 `docker ps` 결과에 새 SHA 기반 이미지가 RUNNING 상태로 보인다
- [ ] 배포 직후 외부에서 HTTP 호출로 정상 응답이 확인된다
- [ ] 엣지 케이스: 기존 컨테이너가 없는 최초 배포 상황에서도 `docker rm` 실패가 전체 스크립트 실패로 이어지지 않는다 (`|| true` 처리)

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] EC2에서 자동 배포 1회 성공 확인
- [ ] `docker ps` 결과 산출물 저장
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 8 SP
```

### **Story 1-3-3. 환경 변수 및 Secrets 안전 주입**

```smalltalk
## Story 1-3-3. 환경 변수 및 Secrets 안전 주입

### User Story
> As a **자격 증명을 안전하게 다루어야 하는 개발자**,
> I want **DB 비밀번호·JWT 시크릿·외부 API 키 등이 코드에 노출되지 않은 채 컨테이너에 주입되길**,
> So that **운영 자격 증명 유출 위험 없이 자동 배포를 운영할 수 있다**.

### 설명
- 민감 정보 분리: GitHub Secrets → EC2 환경 변수 또는 AWS Parameter Store(이미 구축됨)
- v1에서는 EC2 측 `/home/ubuntu/.env` 파일로 단순화
- application.yml은 `${ENV_VAR}` 플레이스홀더 방식 사용
- Parameter Store 직접 연동은 ThirdTool 본 코드 베이스에 이미 적용되어 있으므로 그것과 충돌하지 않도록 정리

### 완료 기준 (Acceptance Criteria)
- [ ] application.yml에 평문 비밀이 존재하지 않고 모두 환경 변수 플레이스홀더로 치환되어 있다
- [ ] EC2 `.env` 또는 Parameter Store 경유로 컨테이너 실행 시 필요한 환경 변수가 모두 주입된다
- [ ] CI/CD 로그에 비밀 값이 평문으로 출력되지 않는다
- [ ] 신규 환경 변수 추가 시 갱신 절차가 README에 정리되어 있다
- [ ] 엣지 케이스: 환경 변수 누락 시 컨테이너가 명확한 에러 메시지와 함께 실패하고, 이전 컨테이너는 그대로 유지 가능한 절차가 정의되어 있다

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] git 히스토리에서 비밀 값 노출 여부 점검 완료
- [ ] 환경 변수 갱신 가이드 README 기록
- [ ] 스테이징 배포 확인

### 스토리 포인트
- 추정: 5 SP
```
