# CI/CD

`.github/workflows/ci.yml`은 PR과 main/dev push에서 Node.js 22, pnpm 9.15.9, JDK 25(Temurin)로 `pnpm lint`와 `pnpm build`를 실행합니다. lint는 웹 타입 검사와 API 컴파일, 주석 컨벤션 검사(`scripts/check-javadoc.py`)를 포함하며 위반이 있으면 실패합니다. build는 Next.js 빌드와 API 실행 JAR(`apps/api/build/libs/damdam-api.jar`)을 만듭니다. 현재 API 테스트는 없으며 DB 서비스 컨테이너도 사용하지 않습니다.

`.github/workflows/deploy.yml`은 수동 실행(`workflow_dispatch`)으로만 배포합니다. push로 운영 서버가 바뀌지 않습니다. 서버에 Docker와 Compose v2, 배포 경로의 완성된 `.env`가 있어야 합니다.

## 배포 설정

GitHub Secrets:

- `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`: 운영 서버 SSH 접속 정보
- `DEPLOY_SSH_PRIVATE_KEY`: 운영 서버 배포 키
- `DEPLOY_KNOWN_HOSTS`: `ssh-keyscan -p <포트> <호스트>` 결과
- `DEPLOY_PATH`: 서버의 배포 경로(필수, 기본값 없음)

Repository variable `HEALTHCHECK_URL`은 `https://damdamwood.co.kr/api/health`입니다.

워크플로는 `DEPLOY_PATH`에 `.env`가 있는지 먼저 확인합니다. `.env`가 없으면 rsync(`--delete`) 전에 중단합니다. 서버의 `.env`는 rsync 대상에서 제외하며, 체크아웃한 코드를 전달한 뒤 `scripts/deploy-server.sh`로 Compose 이미지를 빌드·기동하고 healthcheck를 기다립니다.

첫 배포 전 서버의 배포 경로에 `.env`를 준비합니다. `.env`의 `COMPOSE_PROJECT_NAME`이 운영 중인 프로젝트 이름(`damdam-blog`)과 같아야 볼륨을 이어 쓰며, 다르면 빈 DB로 새 프로젝트가 생깁니다.

```bash
mkdir -p "$DEPLOY_PATH"
cp .env.example "$DEPLOY_PATH/.env"  # 값을 채웁니다
chmod 600 "$DEPLOY_PATH"/.env
grep -q '^COMPOSE_PROJECT_NAME=damdam-blog' "$DEPLOY_PATH"/.env || echo 'COMPOSE_PROJECT_NAME 확인 필요'
```

배포 후에는 배포 경로에서 `docker compose`를 실행합니다.
