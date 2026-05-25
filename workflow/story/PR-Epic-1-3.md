# PR Draft — Epic 1-3 (Docker Hub Push + EC2 Pull CD)

> 브랜치: `feat/005-cd-dockerhub-ec2` → `main`
> 본 문서는 GitHub PR 생성용 *본문 초안*. 작성 주체는 사람 (pr-commit.md "PR 작성 주체" 룰).
> Stories: Story-1-3-1 (Docker Hub 자동 Push) + Story-1-3-2 (EC2 SSH deploy) + Story-1-3-3 (Secrets 안전 주입)

---

## 제목

```
[Infra] feat: Docker Hub Push + EC2 SSH 자동 배포 (CD 파이프라인)
```

## 본문

```markdown
## What
- `.github/workflows/cd.yml` 신규 — `workflow_run("CI" 그린 + main push)` 트리거 → `build-and-push` → `deploy` 2-job 파이프라인
- Docker Hub 자동 Push: `${secrets.DOCKERHUB_USERNAME}/nbc-profile:${SHA}` + `:latest` 병행 (buildx + GHA cache)
- `appleboy/ssh-action@v1` 으로 EC2 SSH → `docker pull / stop / rm / run -d --restart=always --env-file /home/ec2-user/.env`
- `/actuator/health` UP 응답 polling (최대 60s) health gate
- systemd 유닛 `deploy/profile-app.service` 를 *JAR 직접 실행* → *docker 기반* (`docker start -a nbc-profile`) 으로 전면 교체
- `deploy/deploy.sh` 신규 — 수동 운영 보조 (cd.yml inline 과 기능 동일, 롤백 절차)
- README "CD" 섹션 — 트리거 흐름 / Secrets 등록 가이드 / EC2 사전 준비 (1회) / 환경변수 갱신 / 롤백 / 모니터링
- ADR-0013 신규 (Proposed)

## Why
- 현재 운영: 수동 SSH → 새 jar scp → `sudo systemctl restart profile-app` — *휴먼 에러 / 추적 불가 / 롤백 모호*
- 동일 `latest` 만 사용 시 *어떤 커밋이 현재 배포됐는지 불명* → SHA 태그 병행으로 추적성 확보
- 자격증명 (DB password / S3 key / Docker Hub token / SSH key) 코드 노출 0 — GitHub Secrets + EC2 .env 이중 분리
- Epic 1-2 (CI) 의 *trigger 가 되는 안정 신호* 활용 — `workflow_run` 으로 CI 그린 시점 연쇄

## How
- **트리거**: `workflow_run: { workflows: ["CI"], branches: [main], types: [completed] }` + `if: conclusion == 'success' && event == 'push'` — PR 빌드 결과로는 CD 미실행
- **checkout(head_sha)**: CI 가 빌드한 *동일 커밋* 보장
- **buildx + cache-from/to: type=gha**: GitHub Actions cache 로 layer 재사용
- **build-push-action@v5**: SHA + latest 동시 push (1회 빌드, 2회 푸시)
- **ssh-action script_stop: true**: 원격 한 step 실패 시 즉시 abort
- **`|| true` 가드**: 최초 배포 시 컨테이너 부재 → stop/rm 실패 흡수 (Story 1-3-2 엣지 AC)
- **health gate**: 2s × 30 polling, fail 시 `docker logs --tail 100` 출력 후 exit 1
- **새 systemd 유닛**: `docker start -a nbc-profile` (이미 존재하는 컨테이너 attach 모드) — 부팅 자동 기동 + 비정상 종료 시 재시작 안전망. 컨테이너 *최초 생성* 책임은 cd.yml 또는 deploy.sh

## Tradeoff
- **순단 ~5s** (컨테이너 stop → run + JVM warm-up + health gate) — v1 허용, Product 2 ALB+ASG 도입 시 해소
- **단일 EC2 SPOF** — Product 2 후속
- **latest 운영의 risk** — CI 그린이 마지막 안전망. 테스트 커버리지 의존
- **Docker Hub rate limit** (인증 200 pulls/6h) — 단일 EC2 안전. 향후 ASG 시 ECR 또는 캐시 layer
- **Public IP 변동** (Elastic IP 미사용) — 인스턴스 stop/start 시 `EC2_HOST` Secret 수동 회전 필요 (README 절차)
- **CI artifact 미재사용** — cd.yml 이 head_sha 로 docker build 재실행. 빌드 시간 비용 작음 (cache hit) + step 단일화 단순. Follow-up 에서 재검토
- 기각 대안 (ADR-0013 §Alternatives): ECR / Watchtower / SHA-only / systemd 유지 / systemd 제거 / k8s · Helm · ArgoCD / Parameter Store — 각각 학습 곡선 · 시각화 약화 · over-engineering 사유

## Reviewer 종합 (review.md §종합 결과)
- 본 Story 는 Reviewer 5관점 *push 후 별도 단계로 미룸* (사용자 명시 요청).
- 핵심 결정은 ADR-0013 §Decision + §Alternatives 에 명문화 (T4·T1·T3·T7 트리거 통합).
- *의식적 스킵 사유*: CD 파이프라인은 *런타임 코드 변경 0*, Domain/Architecture/API 관점 미적용. Test/Sceptical 관점만 유효 → 첫 실배포 검증 결과 첨부와 함께 발사 가능.

## Test
- [x] 로컬 yml 구조 검증 (`pyyaml safe_load` → keys/jobs/triggers OK)
- [x] `bash -n deploy/deploy.sh` syntax OK
- [ ] 첫 실배포 (사용자 사전 준비 완료 후):
  - GitHub Secrets 4종 등록
  - EC2 docker 설치 + .env 배치 + systemd 유닛 등록 + 첫 컨테이너 생성
  - Epic 1-2 PR + 본 PR 머지
  - main trivial commit push → CI → CD 자동 실행 확인
  - Docker Hub UI 에서 SHA + latest 두 태그 확인
  - EC2 `docker ps` 에 새 컨테이너 RUNNING + `curl http://3.36.121.211:8080/actuator/health` → UP
- [ ] 롤백 절차 1회 검증 (이전 SHA 로 되돌리기)
- [ ] EC2_HOST Secret 회전 절차 (인스턴스 stop/start 후 IP 변경 시) — *실제 회전 발생 시* 확인

## Checklist
- [x] ADR-0013 (Proposed → 첫 실배포 후 Accepted 권장) 작성 + `docs/adr/index.md` 등재
- [x] DOMAIN.md / PACKAGE.md 변경 없음 (런타임 영향 0)
- [x] `application*.yml` 평문 시크릿 0건 확인 (이미 strict placeholder)
- [x] 신규 환경변수 갱신 절차 README 기재
- [ ] Reviewer 5관점 세션 — *미발사 (push 후로 미룸)*
- [ ] *GitHub Branch Protection* (Settings → Branches → main → "Require status checks" + CI 선택) — 사용자 GitHub UI 작업
- [ ] *Docker Hub repo* 사전 생성 (`<user>/nbc-profile`, public/private 선택) — 사용자 작업
- [ ] *GitHub Secrets* 4종 등록 — 사용자 작업

## Follow-ups (ADR-0013 §Follow-ups)
- GitHub Branch Protection 강제
- CI artifact (app-jar) 를 CD 가 재사용 (빌드 시간 절감)
- Docker Hub image retention 정책 (오래된 SHA prune)
- image vulnerability scan (`docker scout` / Trivy)
- SBOM 생성
- ECR 마이그레이션 (트래픽/보안 요구)
- Parameter Store / Secrets Manager (자격증명 회전)
- Blue-Green / ALB 무중단 배포 (Product 2)
- Elastic IP 또는 Route53 자동 갱신 (EC2_HOST 회전 부담 해소)
- Slack/Discord 배포 알림 webhook

## Note on merge conflicts
- 본 PR base 는 main (Epic 1-1 머지 완료, ADR-0011 까지).
- Epic 1-2 PR (feat/004, ADR-0012) 가 *본 PR 보다 먼저* 머지 시 `docs/adr/index.md` 에서 ADR-0012 행이 ADR-0011 과 ADR-0013 사이에 들어가는 *단순 충돌* 예상 — 양쪽 행 모두 채택해 해결.
- Epic 1-2 의 `ci.yml` 은 cd.yml 이 `workflow_run` 으로 *논리적 의존* — 양쪽 모두 main 에 있어야 CD 가 실제로 trigger 됨.

Closes 없음 (이슈 미등록).
```

---

## 권장 `gh` 명령 (사람이 직접 실행)

```powershell
gh pr create `
  --base main `
  --head feat/005-cd-dockerhub-ec2 `
  --title "[Infra] feat: Docker Hub Push + EC2 SSH 자동 배포 (CD 파이프라인)" `
  --body-file workflow/story/PR-Epic-1-3.md
```

또는 GitHub 웹 UI: https://github.com/js-kim-arc/nbc-profile/pull/new/feat/005-cd-dockerhub-ec2

---

## 사용자 사전 준비 체크리스트 (PR 머지 *전* 권장)

| # | 작업 | 위치 |
|---|---|---|
| 1 | Docker Hub repo `<user>/nbc-profile` 생성 (public/private) | hub.docker.com |
| 2 | Docker Hub Access Token 발급 (Read+Write+Delete) | hub.docker.com → Account → Security |
| 3 | GitHub Secrets 4종 등록 (DOCKERHUB_USERNAME/TOKEN, EC2_HOST/SSH_KEY) | GitHub repo → Settings → Secrets and variables → Actions |
| 4 | EC2 docker 설치 + .env 배치 + systemd 유닛 등록 + 첫 컨테이너 (deploy.sh 수동 1회) | EC2 SSH |
| 5 | Epic 1-2 (feat/004) PR 먼저 머지 | GitHub PR |
| 6 | 본 PR 머지 | GitHub PR |
| 7 | main trivial commit push → CI → CD 자동 실행 첫 검증 | git + GitHub Actions UI |
