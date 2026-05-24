# PR Draft — Epic 1-2 (GitHub Actions CI 파이프라인)

> 브랜치: `feat/004-ci-github-actions` → `main`
> 본 문서는 GitHub PR 생성용 *본문 초안*. 작성 주체는 사람 (pr-commit.md "PR 작성 주체" 룰).
> Stories: Story-1-2-1 (트리거 + 기본 구조) + Story-1-2-2 (build/test/캐시/artifact)

---

## 제목

```
[Infra] feat: GitHub Actions CI 파이프라인 (Gradle 캐시 + Artifact)
```

## 본문

```markdown
## What
- `.github/workflows/ci.yml` 신규 — `push:main` + `pull_request:main` 트리거
- Linux `ubuntu-latest` 러너 + JDK 21 Temurin (Epic 1-1 Dockerfile base 와 동일 vendor)
- `gradle/actions/setup-gradle@v4` 네이티브 캐시
- `./gradlew clean build` (compile + test + jar)
- Artifact 2종: `test-reports` (실패 시도 업로드, 7일) + `app-jar` (성공 시, 7일)
- `concurrency.cancel-in-progress` — 동일 PR 새 커밋 push 시 진행 중 실행 자동 취소
- `permissions: contents: read` 최소 권한 + `timeout-minutes: 15` 안전망
- README "CI (GitHub Actions)" 섹션
- ADR-0012 (Proposed)

## Why
- 수동 빌드/테스트 의존 시 *머지 직전 ./gradlew test 잊고 머지* → 깨진 코드가 main 진입 → Epic 1-3 의 CD 가 깨진 jar 배포
- PR 리뷰어가 *로컬 checkout 하지 않고도* 빌드 상태 확인 필요
- Epic 1-3 (Docker Hub Push + EC2 Pull) 의 *trigger 가 되는 안정 신호* 확보 (workflow_run 등)

## How
- `gradle/actions/setup-gradle@v4` — Gradle 도구 팀 권장 캐시 키 자동 관리 (수동 `actions/cache` 회피)
- `./gradlew clean build` — incremental 잔여 영향 제거 → CI 재현성
- 테스트 리포트 `if: always()` — 실패 시도 업로드 → Actions UI 에서 실패 테스트 클래스/라인 확인
- JAR `if: success()` — 빌드 성공 시만 → Epic 1-3 Docker Push 후보 산출물
- `cancel-in-progress` — Actions 분 절약

## Tradeoff
- `clean build` 매 실행 incremental 미활용 → 정확성 우선, ~1-2분 시간 비용 감수
- `paths-ignore` 미적용 (docs/README 단독 변경도 실행) — Story 1-2-1 AC 엣지 케이스 명시. 빌드 시간 부담 시 v2 도입
- Jacoco / Spotless / SpotBugs / Checkstyle 미통합 — T7 (별도 ADR 후)
- public repo 라 GitHub Actions 무료 한도 무관 — private 전환 시 2,000분/월 한도 재검토

## Reviewer 종합 (review.md §종합 결과)
- 본 Story 는 Reviewer 5관점 *push 후 별도 단계로 미룸* (사용자 명시 요청).
- 핵심 결정은 ADR-0012 §Decision + §Alternatives 에 명문화 (T4·T1·T3·T7 트리거 통합).
- *의식적 스킵 사유*: CI 파이프라인은 *런타임 코드 변경 0*, Domain/Architecture/API 관점이 적용 안 됨. Test/Sceptical 관점만 유효 → push 후 fail 시뮬레이션 결과 첨부와 함께 발사 가능.

## Test
- [x] 로컬 `./gradlew clean build` 그린 확인 (1m 14s, 11개 테스트 통과)
- [ ] Actions UI 실행 결과 첨부 (push 후):
  - 1회차 실행 시간 / 캐시 미적용
  - 2회차 실행 시간 / 캐시 적중 — 단축률
- [ ] Story 1-2-2 DoD "의도적 실패 테스트로 차단 동작 검증 1회" — *별도 throwaway 커밋 push → CI fail 확인 → revert* 절차로 진행 예정. 결과 스크린샷 첨부
- [ ] artifact 다운로드 가능 여부 (Actions UI 하단)

## Checklist
- [x] ADR-0012 (Proposed → Reviewer/실측 후 Accepted) 작성 + `docs/adr/index.md` 등재
- [x] DOMAIN.md / PACKAGE.md 변경 없음 (런타임 영향 0)
- [ ] Reviewer 5관점 세션 — *미발사 (push 후로 미룸)*
- [ ] *Branch Protection* 룰 (Settings → Branches → main → "Require status checks") — **사용자 GitHub UI 작업**. CI 가 머지 게이트로 강제되려면 필수

## Follow-ups (ADR-0012 §Follow-ups)
- Jacoco 커버리지 + PR 코멘트
- Spotless · SpotBugs · Checkstyle
- `paths-ignore` 도입 (빌드 시간 부담 시)
- `workflow_run` 으로 Epic 1-3 CD workflow 연계
- GitHub Branch Protection 강제
- matrix build (Java 21 + 25 동시) — 향후 LTS

## Note on merge conflict
- 본 PR base 가 main(Epic 1-1 미머지) 이라 `docs/adr/index.md` 에 ADR-0011 행 부재.
- Epic 1-1 PR (feat/003) 머지 시 ADR-0011 행이 ADR-0010 과 ADR-0012 사이에 들어가는 *단순 충돌* 예상 — 양쪽 행 모두 채택해 해결.

Closes 없음 (이슈 미등록).
```

---

## 권장 `gh` 명령 (사람이 직접 실행)

```powershell
gh pr create `
  --base main `
  --head feat/004-ci-github-actions `
  --title "[Infra] feat: GitHub Actions CI 파이프라인 (Gradle 캐시 + Artifact)" `
  --body-file workflow/story/PR-Epic-1-2.md
```

또는 GitHub 웹 UI: https://github.com/js-kim-arc/nbc-profile/pull/new/feat/004-ci-github-actions
