# 담담공방

Next.js 16 웹과 Spring Boot 4(Java 25) API로 구성합니다. 웹은 pnpm workspaces, API는 Gradle로 빌드합니다. API는 JPA(엔티티 쓰기)와 jOOQ(목록·조인·벌크 갱신·jsonb)를 함께 사용하고 스키마는 Flyway로 관리합니다. 업무 데이터와 로그인 세션(Spring Session JDBC)은 PostgreSQL 16에 저장하며 Redis는 사용하지 않습니다. 결제 수단은 무통장 입금 하나입니다. 가입·로그인은 아이디·비밀번호 방식이며 이메일이나 소셜 계정을 받지 않습니다.

## Docker Compose 실행

```bash
git clone https://github.com/gjaku1031/damdam.git
cd damdam
cp .env.example .env
```

`.env`의 `POSTGRES_PASSWORD`를 채웁니다. 로그인 세션은 Spring Session이 PostgreSQL에 저장하며 별도 세션 비밀키는 필요하지 않습니다. 운영자 아이디는 `SEED_ADMIN_USERNAME=admin`, 초기 비밀번호는 영문·숫자를 포함한 8~128자의 `SEED_ADMIN_PASSWORD`로 주입합니다. 운영자·약관·기본 운영 설정만 준비합니다.

```bash
docker compose up -d --build --wait
docker compose ps
```

웹과 API는 `http://localhost:3000` 및 `http://localhost:3000/api`로 접근합니다. 웹이 내부 API로 프록시하며 API 컨테이너는 외부에 포트를 공개하지 않습니다. PostgreSQL의 호스트 포트는 로컬 주소에만 바인딩합니다.

- `web`: Next.js standalone 웹
- `api`: Spring Boot API, 시작 시 Flyway 마이그레이션 실행
- `postgres`: 업무 데이터와 로그인 세션, `postgres_data` 볼륨
- `uploads`: 업로드 파일 볼륨

DB 스키마 자동 생성은 사용하지 않고, 시작 시 Hibernate가 엔티티와 스키마 일치만 검증합니다. 상품·고객·주문 목데이터를 자동 생성하지 않습니다.

운영자는 고객 화면에서 **편집 모드**를 켜 상품·재고·게시글·공지·FAQ를 수정합니다. 별도 관리자 화면은 없으며 주문·회원·설정·운영 기록은 내 계정에서 관리합니다. [운영 안내](docs/blog-operations.md)를 참고하세요.

## 로컬 개발

Node.js 22, pnpm 9.15.9, JDK 25를 사용합니다. `.env`의 `DB_*` 설정을 Compose 자격 증명에 맞춰 채웁니다. API는 `apps/api`에서 실행할 때 저장소 루트의 `.env`를 읽습니다.

```bash
corepack enable
pnpm install --frozen-lockfile
docker compose up -d postgres
pnpm dev
```

웹은 3000, API는 4000 포트를 사용합니다. `/signup`에서 아이디·비밀번호·비밀번호 확인과 필수 약관 동의로 가입하고 자동 로그인합니다. 아이디는 대소문자를 구분하지 않는 영문·숫자·밑줄 4~20자이며 비밀번호는 영문과 숫자를 포함한 8자 이상(최대 128자)입니다.

## 검증

API는 컴파일과 주석 컨벤션 검사([docs/convention](docs/convention/), `scripts/check-javadoc.py`)를 `pnpm lint`에서, 단위·통합 테스트를 `pnpm test`에서 실행합니다. 통합 테스트는 실제 서버를 임의 포트로 띄우고 Testcontainers의 PostgreSQL 16에 연결해 HTTP로 검증하므로 Docker가 실행 중이어야 합니다. 테스트 코드는 `apps/api/src/test/java`에 있으며 주석 컨벤션을 함께 따릅니다.

```bash
pnpm lint
pnpm test
pnpm build
# API만: cd apps/api && ./gradlew test / ./gradlew bootRun / ./gradlew bootJar
```

브라우저 화면은 자동 테스트 대상이 아닙니다. 화면을 바꾸면 Compose나 로컬 개발 서버를 띄워 실제 브라우저로 확인합니다.

CI는 웹 타입 검사, API 컴파일·주석 검사, API 테스트, 빌드를 실행합니다. [코드 컨벤션](docs/convention/CODE.md), [서버 운영](docs/server-deploy.md), [CI/CD](docs/cicd.md)를 참고하세요.
