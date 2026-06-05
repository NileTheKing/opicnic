#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${DOMAIN:-opicnic.xyz}"
UPSTREAM="${OPICNIC_UPSTREAM:-http://127.0.0.1:18080}"
CERT_PATH="${CLOUDFLARE_ORIGIN_CERT:-/etc/cloudflare/origin.pem}"
KEY_PATH="${CLOUDFLARE_ORIGIN_KEY:-/etc/cloudflare/private.key}"
SITE_NAME="${SITE_NAME:-opicnic}"

if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: run with sudo"
  echo "  sudo DOMAIN=${DOMAIN} $0"
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "ERROR: host nginx is not installed."
  echo "Install it first if this VM does not have host nginx:"
  echo "  sudo apt update && sudo apt install -y nginx"
  exit 1
fi

if [ ! -f "$CERT_PATH" ] || [ ! -f "$KEY_PATH" ]; then
  echo "ERROR: Cloudflare Origin Certificate files not found."
  echo "  cert: $CERT_PATH"
  echo "  key : $KEY_PATH"
  exit 1
fi

mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled

if [ -d /etc/nginx ]; then
  BACKUP="/etc/nginx.backup.$(date +%Y%m%d_%H%M%S)"
  cp -a /etc/nginx "$BACKUP"
  echo "Backed up /etc/nginx to $BACKUP"
fi

cat > "/etc/nginx/sites-available/${SITE_NAME}" <<EOF
server {
    listen 80;
    server_name ${DOMAIN} www.${DOMAIN};

    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl http2;
    server_name ${DOMAIN} www.${DOMAIN};

    ssl_certificate ${CERT_PATH};
    ssl_certificate_key ${KEY_PATH};

    client_max_body_size 150M;

    location / {
        proxy_pass ${UPSTREAM};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 120s;
    }
}
EOF

ln -sf "/etc/nginx/sites-available/${SITE_NAME}" "/etc/nginx/sites-enabled/${SITE_NAME}"

if [ -L /etc/nginx/sites-enabled/default ] || [ -f /etc/nginx/sites-enabled/default ]; then
  rm -f /etc/nginx/sites-enabled/default
fi

nginx -t
systemctl reload nginx 2>/dev/null || systemctl restart nginx

echo "Installed host nginx route:"
echo "  https://${DOMAIN} -> ${UPSTREAM}"
