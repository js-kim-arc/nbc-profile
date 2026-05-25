#!/usr/bin/env bash
# 결정 근거: docs/adr/0013-cd-dockerhub-ec2.md
#
# EC2 위에서 사람이 직접 재배포할 때 사용 (수동 운영 보조).
# 정상 시 cd.yml 의 inline script 가 자동 실행 — 본 스크립트는 *참고/긴급* 용.
#
# 사용:
#   DOCKERHUB_USERNAME=<user> ./deploy.sh          # latest 태그
#   DOCKERHUB_USERNAME=<user> ./deploy.sh <sha>    # 특정 SHA (롤백)
#
# 사전 조건:
#   - docker 설치 + ec2-user 가 docker 그룹
#   - /home/ec2-user/.env 존재 (chmod 600)

set -euo pipefail

: "${DOCKERHUB_USERNAME:?DOCKERHUB_USERNAME env var required}"

TAG="${1:-latest}"
IMAGE="${DOCKERHUB_USERNAME}/nbc-profile:${TAG}"
ENV_FILE="/home/ec2-user/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found — README 'EC2 사전 준비' 참조" >&2
  exit 1
fi

echo "[1/4] docker pull $IMAGE"
docker pull "$IMAGE"

echo "[2/4] stop/rm 기존 컨테이너 (부재 시 || true 흡수)"
docker stop nbc-profile 2>/dev/null || true
docker rm   nbc-profile 2>/dev/null || true

echo "[3/4] docker run -d --restart=always"
docker run -d \
  --name nbc-profile \
  --restart=always \
  -p 8080:8080 \
  --env-file "$ENV_FILE" \
  "$IMAGE"

echo "[4/4] health gate (최대 60s)"
for i in $(seq 1 30); do
  if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
    echo "deploy OK (i=$i)"
    docker ps --filter name=nbc-profile --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
    exit 0
  fi
  sleep 2
done

echo "ERROR: health check timeout — 컨테이너 로그:" >&2
docker logs --tail 100 nbc-profile || true
exit 1
