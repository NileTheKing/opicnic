#!/bin/bash
set -e

# .env 파일 필수 확인
if [ ! -f .env ]; then
  echo "ERROR: .env 파일이 없습니다. .env.example을 참고하여 작성하세요."
  exit 1
fi

source .env

# nginx.conf의 ${DOMAIN} 치환
mkdir -p docker/nginx
sed "s/\${DOMAIN}/$DOMAIN/g" docker/nginx/nginx.conf.template > docker/nginx/nginx.conf

# 빌드 & 배포
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --build

echo "배포 완료: https://$DOMAIN"
echo "주의: VM host Nginx가 80/443을 점유하고, $DOMAIN 요청을 http://127.0.0.1:18080 으로 proxy_pass해야 합니다."
