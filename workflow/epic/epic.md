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
```

## **Epic 1-3. Docker Hub 기반 CD 파이프라인 (Image Push & EC2 Pull)**

```smalltalk
# Epic 1-3. Docker Hub 기반 CD 파이프라인 (Image Push & EC2 Pull)

## Epic 목표
> CI 성공 시점부터 이미지가 자동으로 Docker Hub에 Push되고,
> EC2가 새 이미지를 Pull/Run하여 코드 푸시 한 번으로 배포까지 이어진다.

## 배경
- CI 후 수동으로 EC2에 SSH 접속해서 docker pull/run을 수행하면 휴먼 에러 발생
- 동일 태그(latest)만 사용 시 어떤 커밋이 현재 배포됐는지 추적 불가 — 롤백도 불가
- Docker Hub 자격 증명·EC2 SSH 키를 코드에 노출하면 안 됨 → Secrets로 관리
- 배포 스크립트가 EC2에서 안정적으로 실행되어야 하며, 컨테이너 교체 시 순간적 다운타임은 v1에서는 허용

## Epic 수준 완료 기준 (Definition of Done)
- [ ] CI 성공 후 Docker Hub로 이미지가 자동 Push된다
- [ ] EC2가 새 이미지를 자동 Pull/Run하여 배포가 완료된다
- [ ] 이미지 태깅 전략이 정의되어 있다 (커밋 SHA + latest 병행)
- [ ] 모든 자격 증명이 GitHub Secrets로 관리된다
- [ ] 연결된 스토리가 모두 Done 상태다

## 내부 메모 / 제약 사항
- 기술 제약: GitHub Actions Secrets로 DOCKERHUB_USERNAME / DOCKERHUB_TOKEN / EC2_SSH_KEY / EC2_HOST 관리
- 기술 제약: EC2 접근은 SSH 키 기반 (appleboy/ssh-action 활용)
- 기획 원칙: 이미지 태그는 git SHA(추적성) + latest(편의성) 병행 Push
- 기획 원칙: v1에서는 순단 허용 — 무중단 배포는 Product 2의 ALB+ASG 단계에서 해결
- 연기된 항목: 블루-그린 / 카나리 배포 — v2
- 연기된 항목: ECR로의 마이그레이션 — v2
```
