## **Epic 1-2. GitHub Actions CI 파이프라인 (Build & Test)**

```smalltalk
# Epic 1-2. GitHub Actions CI 파이프라인 (Build & Test)

## Epic 목표
> main 브랜치로의 변경 시점에 자동으로 빌드와 테스트가 실행되어
> 깨진 코드가 머지되거나 배포 단계로 진입하지 않도록 게이트가 작동한다.

## 배경
- 수동 빌드/테스트는 휴먼 에러 발생 — 테스트 실행을 잊고 머지하는 경우가 생김
- 테스트 실패 코드가 배포 단계로 흘러가면 서비스 장애 위험
- PR 시점에 빌드 상태가 보여야 리뷰어가 안전하게 머지 결정 가능
- 이후 CD 단계의 트리거가 되는 안정적 신호가 필요함

## Epic 수준 완료 기준 (Definition of Done)
- [ ] main push와 PR 생성 시점에 워크플로우가 자동 실행된다
- [ ] 빌드와 테스트가 모두 성공해야 다음 단계(CD) 진입 가능하다
- [ ] 실패 시 GitHub Actions UI에서 명확한 로그 확인이 가능하다
- [ ] 빌드 시간이 캐시 활용으로 측정 가능한 수준 단축된다
- [ ] 연결된 스토리가 모두 Done 상태다

## 내부 메모 / 제약 사항
- 기술 제약: Gradle Wrapper 기반, JDK 21 환경
- 기술 제약: actions/setup-java + Gradle 캐시 액션 사용
- 기획 원칙: PR에서도 동일 워크플로우 실행 — 머지 전 안전망 확보
- 연기된 항목: 테스트 커버리지 리포트(Jacoco) 자동 발행 및 PR 코멘트 — v2
- 연기된 항목: 정적 분석(SpotBugs, Checkstyle) 통합 — v2