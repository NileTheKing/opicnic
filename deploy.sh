#!/bin/bash
set -e

# .env 파일 필수 확인
if [ ! -f .env ]; then
  echo "ERROR: .env 파일이 없습니다. .env.example을 참고하여 작성하세요."
  exit 1
fi

source .env

# Cloudflare Origin Certificate 확인
if [ ! -f /etc/cloudflare/origin.pem ] || [ ! -f /etc/cloudflare/private.key ]; then
  echo "ERROR: /etc/cloudflare/origin.pem 또는 private.key 파일이 없습니다."
  exit 1
fi

# nginx.conf의 ${DOMAIN} 치환
mkdir -p docker/nginx
sed "s/\${DOMAIN}/$DOMAIN/g" docker/nginx/nginx.conf.template > docker/nginx/nginx.conf

# 빌드 & 배포
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --build

echo "배포 완료: https://$DOMAIN"
