# Host Nginx Routing

OPIcnic compose의 `opicnic_nginx`는 VM의 80/443을 직접 점유하지 않는다.
VM host Nginx가 정문 역할을 하고, 도메인별로 각 프로젝트 nginx 컨테이너에 라우팅한다.

## Target Architecture

```text
Cloudflare
  -> VM host Nginx :80/:443
     -> opicnic.xyz       -> 127.0.0.1:18080 -> opicnic_nginx -> opicnic_app:8080
     -> axon.opicnic.xyz  -> 127.0.0.1:28080 -> axon_nginx
```

## OPIcnic Compose Port

`docker-compose.prod.yml` exposes only the loopback HTTP port:

```yaml
ports:
  - "127.0.0.1:18080:80"
```

OPIcnic 내부 nginx는 프로젝트 전용 reverse proxy/static serving 역할만 유지한다.
VM 전체의 80/443은 host Nginx가 점유한다.

## Recommended TLS Termination

최종 권장은 host Nginx에서 Cloudflare Origin Certificate로 TLS를 종료하는 것이다.
이렇게 해야 여러 프로젝트가 같은 VM에서 독립적으로 올라가도 80/443 소유권이 host Nginx 하나로 고정된다.

OPIcnic 내부 nginx는 HTTP만 받는다. 따라서 `/etc/cloudflare` 볼륨 마운트와 내부 nginx의 `listen 443 ssl` 설정은 제거한다.

## Host Nginx Example

```nginx
server {
    listen 80;
    server_name opicnic.xyz;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name opicnic.xyz;

    ssl_certificate /etc/cloudflare/origin.pem;
    ssl_certificate_key /etc/cloudflare/private.key;

    client_max_body_size 150M;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 120s;
    }
}
```

Axon은 별도 compose에서 `127.0.0.1:28080:80` 같은 포트로 노출한 뒤, host Nginx에 `axon.opicnic.xyz` 서버 블록을 추가한다.

## Deployment Notes

### First-Time VM Setup

```bash
cd ~/opicnic
git pull

# OPIcnic nginx가 80/443을 내려놓고 127.0.0.1:18080만 열도록 재배포
./deploy.sh

# host nginx 정문 설정 최초 1회 설치
sudo ./scripts/install-host-nginx-opicnic.sh
```

확인:

```bash
docker ps --format 'table {{.Names}}\t{{.Ports}}'
sudo ss -ltnp | grep -E ':80|:443|:18080'
```

기대 상태:

```text
opicnic_nginx -> 127.0.0.1:18080->80/tcp
host nginx    -> 0.0.0.0:80, 0.0.0.0:443
```

### Normal OPIcnic Deploy

host Nginx 정문 설정은 매번 건드리지 않는다.

```bash
cd ~/opicnic
git pull
./deploy.sh
```

### Host Nginx Script

`scripts/install-host-nginx-opicnic.sh`는 `/etc/nginx`를 수정하는 최초 1회용 스크립트다.
`deploy.sh`에서 자동 호출하지 않는다.

환경변수로 조정 가능:

```bash
sudo DOMAIN=opicnic.xyz \
  OPICNIC_UPSTREAM=http://127.0.0.1:18080 \
  CLOUDFLARE_ORIGIN_CERT=/etc/cloudflare/origin.pem \
  CLOUDFLARE_ORIGIN_KEY=/etc/cloudflare/private.key \
  ./scripts/install-host-nginx-opicnic.sh
```

## Do Not Say

- OPIcnic Nginx 제거

## Say Instead

- OPIcnic Nginx를 프로젝트 내부 reverse proxy로 분리
- VM 80/443은 host Nginx가 담당
- Cloudflare Origin Certificate는 host Nginx에서 종료
