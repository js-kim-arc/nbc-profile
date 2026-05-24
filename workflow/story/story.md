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