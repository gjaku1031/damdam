# Cloudflare Tunnel

Cloudflare Tunnel is the public ingress. Do not add nginx or caddy for this deployment.

## Current Server State

- `cloudflared` is installed.
- `cloudflared` systemd service is active.
- Target tunnel hostname: `damdamwood.co.kr`.
- Local app health is passing:
  - Web: `http://127.0.0.1:3000`
  - API: `http://127.0.0.1:4000/api/products`
- Current remote tunnel config observed after the domain correction was `damdamwoodshop.co.kr` -> `http://localhost:3000`.
- Because of that remote config, `damdamwood.co.kr` resolves through Cloudflare but returns `404` until the dashboard-managed ingress hostname is changed.

## Required Routing

The app has two local services:

| Service | Local URL |
| --- | --- |
| Web | `http://localhost:3000` |
| API | `http://localhost:4000` |

Configure Cloudflare Tunnel so the public site reaches both services.

Recommended:

- `damdamwood.co.kr` -> `http://localhost:3000`

The Next.js app rewrites `/api/*` to the local API, so this single-host rule is enough for the current deployment.

Optional explicit split:

- `damdamwood.co.kr` -> `http://localhost:3000`
- `damdamwood.co.kr/api/*` -> `http://localhost:4000`

Alternative:

- `damdamwood.co.kr` -> `http://localhost:3000`
- `api.damdamwood.co.kr` -> `http://localhost:4000`

If using the alternative API hostname, update `NEXT_PUBLIC_API_BASE_URL` in `.env.production` before building.

## Checks

```bash
systemctl status cloudflared
journalctl -u cloudflared -n 100 --no-pager
curl -fsS http://127.0.0.1:3000
curl -fsS http://127.0.0.1:4000/api/products
curl -fsS https://damdamwood.co.kr
```

If public health still fails, check the active remote ingress from the server:

```bash
journalctl -u cloudflared -n 100 --no-pager | grep 'Updated to new configuration'
```

The hostname must be `damdamwood.co.kr`, and the service must be `http://localhost:3000`.
