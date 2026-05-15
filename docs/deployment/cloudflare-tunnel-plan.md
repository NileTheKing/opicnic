# 🚀 Opicnic 배포 계획서: Cloudflare Tunnel (Zero-Trust Architecture)

본 문서는 로컬 개발 환경의 연산 자원(특히 고성능을 요구하는 STT Worker)을 그대로 활용하면서, 지인 테스트 및 외부 시연을 위해 안전하게 서비스를 배포하기 위한 **Cloudflare Tunnel 도입 계획**임.

---

## 1. 배포 아키텍처 개요 (The Architecture)

전통적인 포트 포워딩이나 클라우드 VM 임대 방식을 탈피하고, **Outbound-Only** 터널링 방식을 통해 인프라 구축 비용 없이 강력한 보안과 HTTPS 통신을 확보함.

```mermaid
graph LR
    subgraph Local PC (Your Mac)
    DB[MySQL Docker]
    STT[FastAPI Worker]
    App[Spring Boot :8080]
    Daemon[cloudflared]
    end
    
    subgraph Cloudflare Edge
    Tunnel[Encrypted Tunnel]
    WAF[Web Application Firewall]
    end
    
    Users[External Users] -- "HTTPS (Anycast IP)" --> WAF
    WAF -- "QUIC Protocol" --> Tunnel
    Tunnel -- "Relay" --> Daemon
    Daemon -- "Localhost Proxy" --> App
    
    App <--> DB
    App <--> STT
```

---

## 2. 사전 준비물 (Prerequisites)
- [ ] **Cloudflare 계정**: 무료 가입 필수
- [ ] (선택 사항) **개인 도메인**: Cloudflare에 등록된 도메인이 있다면 `opicnic.yourdomain.com` 형태로 깔끔하게 배포 가능. 없다면 Cloudflare가 제공하는 무료 임시 도메인(`trycloudflare.com`) 사용 가능.
- [ ] **로컬 환경 구동 확인**: `docker-compose up -d` 및 `./gradlew bootRun`을 통해 로컬 `localhost:8080`에서 접속됨을 최종 확인.

---

## 3. 단계별 실행 계획 (Implementation Steps)

### Step 1: `cloudflared` 데몬 설치 (macOS 기준)
Cloudflare Edge 서버와 로컬 PC를 연결해줄 에이전트를 설치함.
```bash
brew install cloudflare/cloudflare/cloudflared
```

### Step 2: Cloudflare 계정 인증
CLI에서 로그인하여 터널 생성 권한을 획득함.
```bash
cloudflared tunnel login
# 브라우저가 열리면 인증 완료
```

### Step 3: 터널 생성 및 라우팅 설정
Opicnic 전용 터널을 뚫고, 로컬 서버(8080)로 길을 안내함.
```bash
# 터널 생성 (이름: opicnic-tunnel)
cloudflared tunnel create opicnic-tunnel

# 도메인 연결 (예: 개인 도메인이 있는 경우)
cloudflared tunnel route dns opicnic-tunnel opicnic.yourdomain.com

# 로컬 8080 포트와 연결하는 설정 파일 작성 (~/.cloudflared/config.yml)
# ingress:
#   - hostname: opicnic.yourdomain.com
#     service: http://localhost:8080
#   - service: http_status:404
```

*참고: 무료 임시 도메인으로 가볍게 1회성 배포만 할 경우, 위 복잡한 과정 없이 아래 명령어 하나면 끝남.*
```bash
cloudflared tunnel --url http://localhost:8080
```

### Step 4: 터널 백그라운드 실행
터미널을 꺼도 서비스가 유지되도록 백그라운드 서비스로 등록함.
```bash
cloudflared service install
cloudflared service start
```

---

## 4. 운영 및 유지보수 전략 (Maintenance)
- **로컬 PC 가동 필수**: 배포가 유지되려면 로컬 PC가 켜져 있고 절전 모드에 진입하지 않아야 함. (지인 테스트용으로만 추천하는 이유)
- **보안**: 터널 설정이 완료되면 로컬 방화벽이나 공유기의 포트는 모두 닫아두어도 무방함. 외부 공격(DDoS 등)은 Cloudflare Edge 단에서 1차 차단됨.
- **모니터링**: Cloudflare Zero Trust 대시보드에서 트래픽 추이 및 연결 상태 모니터링 가능.
