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

    client_max_body_size 64M;  # 컨테이너 nginx/톰캣 max-request-size와 동일하게 유지할 것

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

## Resource Limits

앱 컨테이너의 메모리는 명시적으로 잡는다. 명시하지 않으면 JVM이 `MaxRAMPercentage` 기본값 25%를 **호스트 전체 메모리(24GB)** 에 적용해 약 6GB까지 잡는다. 이 VM은 다른 프로젝트(axon 스택 등)와 공유하므로 JVM이 옆 서비스 몫까지 쓰게 된다.

```yaml
# docker-compose.prod.yml, app 서비스
mem_limit: 6g
environment:
  - JAVA_TOOL_OPTIONS=-Xmx5g   # 힙 5GB + 비힙 약 1GB
```

적용 확인:

```bash
docker logs opicnic_app 2>&1 | grep "Picked up JAVA_TOOL_OPTIONS"
docker exec opicnic_app sh -c 'java -XX:+PrintFlagsFinal -version | grep MaxHeapSize'
```

## Upload Size Limits

업로드 상한은 **4겹이 같은 값을 바라봐야 한다.** 한 곳만 바꾸면 바깥쪽에서 먼저 잘리거나 안쪽이 무의미해진다.

| 위치 | 값 | 파일 |
|---|---|---|
| 브라우저 녹음 시간 | 120초 | `templates/practice/question.html` (`MAX_RECORDING_SECONDS`) |
| 호스트 nginx | 64M | `/etc/nginx/sites-available/opicnic` (**VM에만 존재**) |
| 컨테이너 nginx | 64M | `docker/nginx/nginx.conf.template` |
| 톰캣 | 4MB / 64MB | `application.yml` (`max-file-size` / `max-request-size`) |
| 컨트롤러 | 4MB | `PracticeAttemptApiController.MAX_ANSWER_FILE_BYTES` |

근거는 2분 녹음 webm/opus 실측 0.5~1.3MB(`scripts/test_1m20s.webm` 80초 844KB = 84.4kbps). 64MB는 모의고사 15문항을 한 요청에 담는 현 제출 구조 기준(15 × 4MB + 여유)이다.

**호스트 nginx는 리포에 없다.** 위 `Host Nginx Example`과 실제 VM 파일을 함께 고쳐야 한다.

## Monitoring

Prometheus 스크레이프 대상은 **compose 서비스 이름**을 쓴다.

```yaml
# docker/prometheus/prometheus.prod.yml
- targets: ['app:8080']      # O
- targets: ['opicnic_app:8080']   # X — 400 Bad Request
```

`container_name`을 쓰면 그 이름이 그대로 `Host` 헤더에 실리는데, 톰캣이 도메인 이름의 언더스코어를 거부한다. 브라우저 트래픽은 nginx가 `Host`를 실제 도메인으로 바꿔주기 때문에 멀쩡해서, **앱은 정상인데 메트릭만 안 들어오는 형태로 조용히 깨진다.**

설정 파일은 바인드 마운트라 `compose up -d`로는 다시 안 읽힌다. 컨테이너를 재시작해야 한다.

```bash
docker restart opicnic_prometheus
docker exec opicnic_prometheus wget -qO- 'http://localhost:9090/api/v1/targets?state=any'
```

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
