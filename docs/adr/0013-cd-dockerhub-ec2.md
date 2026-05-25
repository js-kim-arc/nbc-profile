# ADR-0013: Docker Hub CD + SHA·latest 태깅 + SSH deploy + GitHub Secrets→EC2 .env

- **Status**: Proposed
- **Date**: 2026-05-25
- **Deciders**: junseong kim
- **Trigger**: T4 (Docker Hub · appleboy/ssh-action 도입), T1 (Docker Hub vs ECR / SSH-push vs Watchtower / SHA-only vs SHA+latest / 단일 systemd vs docker--restart=always 단독), T3 (이미지 태깅 컨벤션 신규), T7 (Blue-Green · 카나리 · k8s · Helm · ArgoCD · ECR · Parameter Store 연기)

## Context

Epic 1-1 (ADR-0011) 로 *멀티스테이지 Dockerfile* 까지, Epic 1-2 (ADR-0012) 로 *GitHub Actions CI* 까지 완료. 그러나 *코드 푸시 → 운영 환경 갱신* 의 마지막 자동화 단계가 비어있음.

현재 운영 절차:
1. 개발자가 SSH (`ssh -i profile-key.pem ec2-user@3.36.121.211`) 로 EC2 접속
2. 새 jar 를 scp/curl 로 받음
3. `sudo systemctl restart profile-app` (기존 systemd 유닛이 `java -jar /home/ec2-user/app.jar` 실행)

문제:
- *수동 SSH* — 휴먼 에러 (잘못된 jar 받기, 재시작 누락)
- 동일 `latest` 태그 운영 — *어떤 커밋이 현재 배포됐는지 추적 불가* → 롤백도 *어떤 시점으로* 가 모호
- 자격증명 (DB password / S3 key / Docker Hub token / SSH key) 이 *코드에 노출되면 안 됨*
- 단일 EC2 인스턴스 (t3.micro) — *컨테이너 교체 시 순단 ~5s 허용* (v1)
- ALB / ASG / 무중단 배포는 Product 2 의 후속 — *지금은 의식적으로 미적용*

EC2 사전 조건:
- AL2023 + ec2-user (Story 본문 `/home/ubuntu` 잔재는 무시 — 본 환경은 ec2-user)
- 22/80/8080 SG 개방 (My IP /32) — public IP 변동성 (Elastic IP 미사용) 으로 SG 갱신 절차 README 기록됨
- Docker 미설치 (사용자 1회 수동 설치 예정)

## Decision

**`workflow_run` trigger 기반 별도 `cd.yml` + `docker/build-push-action` (SHA + latest 병행) + `appleboy/ssh-action` 기반 EC2 deploy + docker `--restart=always` + 새 docker 기반 systemd 유닛 (부팅 안전망) + GitHub Secrets → EC2 `/home/ec2-user/.env`** 채택:

### `.github/workflows/cd.yml`

```yaml
on:
  workflow_run:
    workflows: ["CI"]
    branches: [main]
    types: [completed]

jobs:
  build-and-push:
    if: >
      github.event.workflow_run.conclusion == 'success' &&
      github.event.workflow_run.event == 'push'
    # checkout(head_sha) → buildx → login → build-push (sha+latest, type=gha cache)

  deploy:
    needs: build-and-push
    # appleboy/ssh-action → docker pull → stop/rm/run --restart=always → health gate
```

### 핵심 규칙
- **별도 cd.yml**: CI 와 *책임 분리* (ci.yml = 게이트 / cd.yml = 배포). 트리거 / 권한 / 캐시 키 / Secrets 노출 표면 분리.
- **`workflow_run` trigger**: CI 그린 시점에 자동 연쇄 — *CI 실패 시 CD 미실행* 보장. `event == 'push'` 가드로 PR 워크플로 결과로는 CD 안 돔.
- **SHA + latest 병행**: 추적성(SHA) + 편의성(latest). 롤백 = `docker pull <user>/nbc-profile:<sha> && docker tag ... :latest && systemctl restart`.
- **`build-push-action` + `cache-from/to: type=gha`**: GitHub Actions 캐시로 layer 재사용 — main 누적 빌드 시간 안정화.
- **`appleboy/ssh-action`**: Docker Hub Push 후 EC2 측에서 *pull/run* 능동 수행. Watchtower 같은 *EC2 측 자동 pull* 대비 *언제 배포됐나* 가 Actions log 에 시각화.
- **docker `--restart=always`**: 컨테이너 비정상 종료 시 docker daemon 이 자동 재시작.
- **새 systemd 유닛**: 부팅 시 컨테이너 자동 기동 안전망 (`docker start -a nbc-profile`). 컨테이너 *최초 생성* 은 deploy 스크립트가 `docker run -d` 로 수행 → systemd 는 lifecycle 보조 역할.
- **Secrets → EC2 .env 분리**: GitHub Secrets 는 *CI/CD workflow 의 자격증명* (Docker Hub / SSH). 앱 런타임 환경변수 (DB / S3) 는 *EC2 측 `/home/ec2-user/.env`* (chmod 600) — *GitHub Actions log 노출 0*. `docker run --env-file` 로 주입.

### GitHub Secrets 목록 (사용자 등록)
| Secret | 용도 |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 사용자명 — 이미지 경로 prefix |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token (write 권한) |
| `EC2_HOST` | EC2 Public IP (Elastic IP 미사용 → 변동 시 갱신) |
| `EC2_SSH_KEY` | `profile-key.pem` 전체 내용 (BEGIN/END 라인 포함) |

## Alternatives Considered

### Option A. ECR (AWS Elastic Container Registry)

- **Pros**: AWS 생태계 통합 · IAM Role 인증 (Docker Hub token 불필요) · private repo 무료 (500MB) · pull rate limit 없음.
- **Cons**: 학습 곡선 + IAM 설계 추가. CI 가 AWS credentials 필요 → OIDC 또는 access key 추가 Secret. EC2 측 `aws ecr get-login-password` 추가 step.
- **기각 이유**: T7. 학습 단계 — Docker Hub 가 *zero AWS friction*. 향후 트래픽/보안 요구 도달 시 마이그레이션.

### Option B. Watchtower (EC2 측 자동 pull)

- **Pros**: deploy job 불필요 — EC2 가 자체적으로 polling. SSH key 노출 0.
- **Cons**: *언제 배포됐나* 가 GitHub Actions 밖. 배포 실패 시 알람 자체 구성 필요. polling 주기 trade-off (즉시성 vs 부하).
- **기각 이유**: 학습 컨텍스트에서 *Actions log = single pane of glass* 우선. 배포 트리거가 *명시적으로 push 직후* 발생해야 디버깅 쉬움.

### Option C. SHA-only 태그 (latest 미사용)

- **Pros**: 매 배포가 *명시적 SHA 지정* — 우연한 latest 갱신 사고 없음.
- **Cons**: EC2 측 deploy 스크립트가 *항상 새 SHA 환경변수* 필요. workflow → ssh 로 SHA 전달 필요 (변수 인터폴레이션 복잡).
- **기각 이유**: latest 가 *deploy 스크립트 단순화* 의 핵심. 롤백은 수동 SHA 지정 (드문 케이스 → 단순 절차 유지가 비용 효율).

### Option D. systemd 유지 + JAR 와 docker 병행

- **Pros**: 기존 운영 절차 변동 최소.
- **Cons**: 포트 8080 충돌 — 동시 운영 불가. *언제 어느 게 실제로 도는지* 불명.
- **기각 이유**: 운영 불가능. 단방향 전환 (JAR systemd → docker systemd) 만 가능.

### Option E. docker `--restart=always` 단독, systemd 제거

- **Pros**: 더 단순. docker daemon 의 자체 재시작 메커니즘만 사용.
- **Cons**: docker 가 *부팅 시 자동 기동* 되어도 *컨테이너 자동 기동* 은 `--restart=always` 메타데이터 기반 → docker daemon up 시점에 메타데이터 인식하면 OK. 그러나 *시각화 단일점* 부족 — 운영자가 `systemctl status profile-app` 으로 통일된 상태 확인 못 함.
- **기각 이유**: systemd 유닛 = *운영자 표준 도구로 컨테이너 상태 확인* 가능. 비용 0 으로 시각화 일관성 확보.

### Option F. Helm / ArgoCD / k8s 풀스택

- 기각 이유: T7. 단일 EC2 환경에 k8s 도입 = over-engineering.

### Option G. Parameter Store / Secrets Manager (EC2 측)

- **Pros**: 자격증명 회전 자동화 · IAM Role 기반.
- **Cons**: 추가 SDK 의존성 · 런타임 fetch 코드 · 비용.
- **기각 이유**: T7. 학습 단계 — `.env` 가 zero infra cost. 향후 prod 트래픽 도달 시 마이그레이션.

## Consequences

### Positive

- *코드 push → 운영 환경 갱신* 평균 ~3-5분 자동화 (CI 1-2분 + push 1분 + deploy + health gate ~1분)
- 모든 배포가 *Actions log 에 SHA 기록* — 추적성
- 자격증명 *코드 노출 0*: GitHub Secrets (workflow 측) + EC2 .env (런타임 측) 분리
- 롤백 절차 정의 (`docker pull <sha> && tag latest && systemctl restart`)
- 컨테이너 lifecycle = docker (자가 치유) + systemd (부팅 안전망) 이중 안전

### Negative / Trade-offs

- *순단 ~5s* (컨테이너 stop → run + JVM warm-up + health gate) — v1 허용
- *단일 EC2 SPOF* — Product 2 ALB+ASG 도입 시 해결
- *latest 운영* 의 risk: 사고로 잘못된 SHA → latest 갱신 시 즉시 운영 반영. 보호장치는 *CI 그린* (depends on 테스트 커버리지)
- Docker Hub *rate limit*: 인증 200 pulls/6h. EC2 가 단일 인스턴스 → 안전. *향후 ASG 다인스턴스* 도입 시 ECR 또는 캐싱 layer 검토
- *Public IP 변동* (Elastic IP 미사용): 인스턴스 stop/start 시 EC2_HOST Secret 수동 회전 필요 → README 절차

### Neutral

- ADR-0008 (yml 분리) / ADR-0004 (S3 fallback) / ADR-0010 (actuator) / ADR-0011 (Dockerfile) 정책 *그대로 유지* — 본 ADR 은 *배포 자동화* 만.
- Epic 1-2 (ADR-0012) 의 ci.yml 은 *별도 파일* — cd.yml 이 `workflow_run` 으로 *논리적 의존*. CI artifact (app-jar) 는 *재사용하지 않음* (cd.yml 이 head_sha 로 다시 빌드 — 이미지 캐시 효율로 시간 비용 작음, 빌드/푸시 step 단일화로 단순)
- `appleboy/ssh-action@v1` 메이저 핀 (1.x 마이너 자동) — major bump 시 ADR 갱신
- 이미지 retention 정책 (Docker Hub) 는 본 ADR 범위 밖 — 향후 별도 관리 (수동 prune 또는 자동화)

## Follow-ups

- [ ] GitHub Branch Protection (Settings → Branches) — main 머지 게이트로 CI 필수 (사용자 GitHub UI 작업)
- [ ] CI artifact (app-jar) 를 CD 가 *재사용* 하는 방식으로 빌드 시간 절감 검토 — 현재는 cd.yml 이 docker build 재실행
- [ ] Docker Hub 이미지 retention/cleanup 정책 (오래된 SHA 태그 정리)
- [ ] image vulnerability scan (`docker scout` / Trivy)
- [ ] SBOM 생성 + 첨부
- [ ] ECR 마이그레이션 (트래픽/보안 요구 도달 시)
- [ ] Parameter Store / Secrets Manager (자격증명 회전 자동화 필요 시)
- [ ] Blue-Green / ALB / 무중단 배포 (Product 2 ALB+ASG Epic)
- [ ] Elastic IP 채택 또는 DNS A 레코드 자동 갱신 (Route53) — EC2_HOST Secret 수동 회전 부담 해소
- [ ] Slack/Discord 배포 알림 (성공/실패 webhook)
- *재검토 트리거*: 단일 EC2 → ASG 전환 / Docker Hub 비용 정책 변경 / 보안 인시던트 / 배포 시간 5분 초과 / 순단 허용 정책 변경
