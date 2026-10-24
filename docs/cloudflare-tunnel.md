# Cloudflare Tunnel

운영 사이트(`https://damdamwood.co.kr`)의 공개 입구는 Cloudflare Tunnel 하나입니다. 서버에 nginx·caddy를 두지 않으며 HTTPS와 CDN 캐시도 Cloudflare가 맡습니다.

## 현재 구성

2026-10-13에 운영 서버에서 확인한 상태입니다.

- `cloudflared`는 systemd 서비스로 실행하며 `tunnel run --token <토큰>` 방식입니다. 터널의 호스트 이름과 전달 대상은 서버 파일이 아니라 Cloudflare 대시보드에서 관리합니다.
- 공개 호스트 이름 `damdamwood.co.kr`을 `http://localhost:3000`(웹 컨테이너)으로 전달합니다.
- 웹 컨테이너는 `127.0.0.1:3000`에만 바인딩합니다. API 컨테이너는 호스트에 포트를 열지 않으며 웹이 `/api/*`를 내부 API로 프록시하므로 터널 규칙은 이 하나면 됩니다.
- 이 구성에서 서버 `.env`는 `WEB_ORIGIN=https://damdamwood.co.kr`, `SESSION_COOKIE_SECURE=true`여야 합니다([서버 운영](server-deploy.md)의 도메인과 HTTPS 참고).

## 계정 이전 예정

Cloudflare를 다른 계정으로 옮길 예정입니다. 터널 토큰·DNS·캐시 설정은 모두 계정에 속하므로 옮긴 뒤 새 계정에서 다시 만들어야 합니다. 지금 계정에는 설정을 더 추가하지 않고, 아래 항목을 이전할 때 함께 처리합니다.

1. 새 계정에 도메인을 추가하고 네임서버를 변경합니다.
2. 새 계정에서 터널을 만들고 공개 호스트 이름 `damdamwood.co.kr` → `http://localhost:3000`을 등록합니다.
3. 서버의 `cloudflared` 서비스를 새 터널 토큰으로 다시 설치합니다. 토큰은 저장소와 문서에 적지 않습니다.
4. 캐시 방어를 한 겹 더 둡니다. Cache Deception Armor를 켜고, `/api/*` 가운데 사진 주소만 캐시 대상으로 두는 규칙을 추가합니다. 지금은 API가 모든 응답에 붙이는 `Cache-Control: no-store` 한 겹에만 기대고 있습니다([응답 캐시 설계](response-cache-tradeoffs.md)).
5. 아래 확인을 다시 실행합니다.

## 확인

```bash
systemctl is-active cloudflared
journalctl -u cloudflared -n 100 --no-pager
curl -fsS -o /dev/null http://127.0.0.1:3000/login
curl -fsS https://damdamwood.co.kr/api/health
curl -sI 'https://damdamwood.co.kr/api/products;check.jpg'
```

마지막 명령의 응답은 `cache-control: no-store`이고 `cf-cache-status`가 `HIT`이 아니어야 합니다.

로그인 시도 제한은 API가 `X-Forwarded-For`의 마지막 주소로 방문자를 구분합니다. 터널이나 웹(Next.js)의 버전을 바꾸면, 공인 IP가 다른 두 곳에서 없는 아이디로 틀린 로그인을 보내 한쪽이 한도를 넘겨 429를 받은 뒤에도 다른 쪽이 401을 받는지 확인합니다. 2026-10-24에 이 방법으로 두 방문자가 따로 세어지는 것을 확인했고, 방문자가 `X-Forwarded-For`에 다른 주소를 적어 보내도 제한을 피하지 못했습니다. 방문자가 보낸 `CF-Connecting-IP`는 Cloudflare가 403으로 거절합니다.
