# CI/CD

`.github/workflows/ci.yml`은 PR과 main/dev push에서 Node.js 22, pnpm 9.15.9, JDK 25(Temurin)로 `pnpm lint`와 `pnpm build`를 실행합니다. lint는 웹 타입 검사와 API 컴파일, 주석 컨벤션 검사(`scripts/check-javadoc.py`)를 포함하며 위반이 있으면 실패합니다. build는 Next.js 빌드와 API 실행 JAR(`apps/api/build/libs/damdam-api.jar`)을 만듭니다. 현재 API 테스트는 없으며 DB 서비스 컨테이너도 사용하지 않습니다.

`.github/workflows/deploy.yml`은 수동 실행(`workflow_dispatch`)으로만 배포합니다. 새 저장소 push가 기존 서버를 자동 변경하지 않습니다. 서버에 Docker와 Compose v2, 완성된 `.env`가 있어야 합니다.

필요한 GitHub Secrets:

- `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`
- `DEPLOY_SSH_PRIVATE_KEY`, `DEPLOY_KNOWN_HOSTS`
- `DEPLOY_PATH` (기본 `/home/ubuntu/damdam`)

서버의 `.env`는 rsync 대상에서 제외합니다. 워크플로는 체크아웃한 코드를 전달하고 `scripts/deploy-server.sh`를 실행합니다. 스크립트는 Compose 이미지를 빌드·기동하고 서비스 healthcheck를 기다립니다. 선택적으로 `HEALTHCHECK_URL` repository variable에 공개 사이트 점검 주소를 설정할 수 있습니다.
