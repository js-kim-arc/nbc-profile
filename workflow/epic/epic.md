## **Epic 1-1. 애플리케이션 컨테이너화 (Dockerfile)**

```smalltalk
# Epic 1-1. 애플리케이션 컨테이너화 (Dockerfile)

## Epic 목표
> Spring Boot 애플리케이션이 환경에 무관하게 동일하게 실행되는
> Docker 이미지로 패키징되어, 로컬과 EC2 어디에서나 같은 결과를 만들어낸다.

## 배경
- 로컬 개발 환경(macOS + JDK 21)과 EC2(Ubuntu) 사이 시스템 라이브러리·JDK 버전 차이가 배포 장애의 흔한 원인
- 이미지 크기가 크면 Docker Hub Push/Pull 시간이 길어져 배포 사이클이 늘어남
- 빌드 환경(JDK·Gradle)과 실행 환경(JRE)을 분리해야 이미지가 가벼워지고 보안 표면도 줄어듦

## Epic 수준 완료 기준 (Definition of Done)
- [ ] 멀티스테이지 구조의 Dockerfile이 작성되어 있다
- [ ] 로컬에서 `docker build` → `docker run`으로 애플리케이션이 정상 기동된다
- [ ] 최종 이미지에 빌드 산출물(소스, Gradle 캐시 등)이 포함되지 않는다
- [ ] 이미지 크기가 단일 스테이지 대비 명확히 감소했음이 측정되어 있다
- [ ] 연결된 스토리가 모두 Done 상태다

## 내부 메모 / 제약 사항
- 기술 제약: JDK 21, Spring Boot 3.x, Gradle Wrapper 사용
- 기획 원칙: 빌드 이미지(JDK + Gradle)와 실행 이미지(JRE only) 명확히 분리
- 기획 원칙: 이미지 베이스는 검증된 공식 이미지(eclipse-temurin) 사용
- 연기된 항목: distroless / Alpine 기반 추가 경량화 — v2
- 연기된 항목: Jib 등 Gradle 플러그인 기반 이미지 빌드 전환 — v2