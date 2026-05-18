# CLAUDE.md

> 본 프로젝트의 **단일 진입 포인터**. 작업 흐름 · 구조 · 컨벤션의 본질은 본 파일에서 시작해 해당 룰 · 문서로 연결된다.

---

## 진입 (이 8개 파일만 알면 충분)

| 무엇을 알고 싶을 때 | 어디를 보나 |
| --- | --- |
| 작업 흐름 / Story 실행 절차 | `.claude/rules/workflow.md` |
| Reviewer 5관점 세션 | `.claude/rules/review.md` |
| 브랜치 · 커밋 · push · PR 규칙 | `.claude/rules/pr-commit.md` |
| 코드 작성 컨벤션 (도메인 · API · DB · 테스트) | `.claude/rules/conventions.md` |
| ADR 작성 트리거 · toolkit 진입 | `.claude/rules/adr.md` |
| 도메인 의도 · 용어 · 불변식 | `docs/DOMAIN.md` |
| 패키지 · BC · 레이어 구조와 의존 규칙 | `docs/PACKAGE.md` |
| 아키텍처 결정 기록 (ADR) | `docs/adr/` (`docs/adr/index.md`) |
| 현재 진행 중인 Epic / Story | `workflow/epic/epic.md`, `workflow/story/story.md` |

본 CLAUDE.md 의 다른 섹션이 위 룰 · 문서와 모순될 경우 **룰 · 문서가 우선**한다 — CLAUDE.md 는 빠른 개요이지 단일 진실 소스가 아니다.

---

## 본 프로젝트 컨텍스트

- **스택**: Spring Boot 4.0.6 · Java 21 · Gradle Kotlin DSL · JPA(Hibernate) · Bean Validation · Spring Web MVC · Lombok · JUnit 5 · H2(개발) / MySQL(운영).
- **상태**: 빈 demo 골격. 도메인 미정 — 첫 Story 진입 시 `docs/DOMAIN.md` 에서 정의.
- **README**: "클라우드 주차 과제".

---

## "코드가 진실 소스" 원칙

별도 명세 문서를 두지 않는 영역:

| 영역 | 진실 소스 |
| --- | --- |
| API 명세 | Controller + Request/Response DTO + Swagger (도입 시) |
| DB 스키마 | DB 마이그레이션 스크립트 + JPA 매핑 어노테이션 |
| 테스트 매트릭스 | 테스트 코드 + 메서드명 (`{대상행위}_{상황}_{기대결과}` 패턴) |
| 패키지 · 디렉토리 사실 | 소스 트리 자체 |
| 인프라 사실 | CI/CD 파이프라인 정의 + 환경 설정 |

이 영역들에 대한 *별도 명세 문서를 만들지 않는다*. ADR 이나 Story 작업이 이 영역을 변경해도 docs 갱신을 요구하지 않는다.

`docs/` 에 두는 것은 **코드만으론 알 수 없는 의도와 결정**뿐 — `DOMAIN.md`, `PACKAGE.md`, `adr/`.

---

## 작업 흐름 요약 (상세는 `workflow.md`)

```
1. 읽기           — Story → Epic → DOMAIN 관련 BC → 코드
2. Plan mode 합의 — 비단순 변경 시 강제
3. 실행           — 의미 단위 커밋
4. Reviewer 5관점 — 변경 요약 사전 보고 → 단일 메시지 병렬 발사 → 사용자 의사결정
5. push 판단      — 사용자 명시 요청 시에만
```

Reviewer 5관점: **Domain / Architecture / API · Exception / Test / Sceptical**. 상세는 `review.md`.

---

## 외부 참조

- ADR Toolkit: `C:/study/System_Author/_library/_adr-toolkit/` — 결정 사고 도구. 새 ADR 작성 전 진입 의무.
- Harness Starter (원본): `C:/study/System_Author/_library/_harness-starter/HARNESS-STARTER.md` — 본 룰 셋의 원본. 본 프로젝트는 §1·3·4·5·6 을 5개 파일로 분할 적용.

---

## 비고

- `.claude/` 는 `.gitignore` 처리되어 **개발자 로컬에만 존재**한다 (운영 룰 분리).
- `docs/`, `CLAUDE.md` 는 tracked — 팀 공유.
- 본 진입표 외의 룰 · 문서 추가/변경 시 본 표도 함께 갱신.
