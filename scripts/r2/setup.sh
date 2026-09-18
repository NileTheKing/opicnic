#!/bin/bash
# R2 버킷·CORS·라이프사이클을 코드로 만든다. 재실행해도 같은 상태로 수렴한다(멱등).
# 전제: `npx wrangler login` 으로 브라우저 승인이 끝나 있어야 한다 (버킷 생성 권한은 앱용 S3 키에 없다).
# 앱용 S3 키(R2_ACCESS_KEY_ID/SECRET)는 여기서 안 쓴다 — 그건 앱 런타임용이고 .env에만 둔다.
set -euo pipefail
cd "$(dirname "$0")"

BUCKET="${R2_BUCKET:-$(grep '^R2_BUCKET=' ../../.env | cut -d= -f2-)}"
[ -n "$BUCKET" ] || { echo "R2_BUCKET이 .env에 없음"; exit 1; }

echo "== 버킷: $BUCKET"
if npx wrangler r2 bucket list 2>/dev/null | grep -qE "^name:\s+$BUCKET\s*\$"; then
  echo "   이미 있음"
else
  npx wrangler r2 bucket create "$BUCKET"
fi

echo "== CORS (브라우저 직접 PUT 허용: opicnic.xyz, localhost:8080)"
npx wrangler r2 bucket cors set "$BUCKET" --file cors.json

echo "== 라이프사이클 (attempts/ 30일, pending/ 1일, 미완 멀티파트 1일)"
npx wrangler r2 bucket lifecycle set "$BUCKET" --file lifecycle.json --force

echo "== 확인"
npx wrangler r2 bucket cors list "$BUCKET"
npx wrangler r2 bucket lifecycle list "$BUCKET"
