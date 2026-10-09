# Docker Compose 서버 운영

Docker Engine과 Compose v2가 설치된 서버에서 `gjaku1031/damdam` 저장소를 사용합니다.

## API 구성과 스키마 변경

API는 Spring Boot 4(Java 25)·JPA·jOOQ·Flyway로 구성하며 컨테이너 포트는 4000입니다.

- **스키마**: 시작 시 Flyway가 `V1__baseline.sql`부터 차례로 적용합니다. 스키마 변경은 새 `V<n>__*.sql`로 추가합니다.
- **세션**: 로그인 세션을 PostgreSQL(Spring Session JDBC, `V2__spring_session.sql`)에 저장합니다. API를 재시작해도 로그인이 유지됩니다. 로그인·가입 시도 제한은 API 메모리 카운터라 재시작하면 초기화됩니다.
- **결제**: 무통장 입금만 지원합니다.
- **배송 메모·사업자 정보**: `V4__order_ship_memo.sql`이 주문에 `shipMemo`(기본 빈 문자열)를 추가합니다. 시작 시 사업자 정보·고객센터 설정 키가 빈 값으로 준비되며, 배포 후 `/mypage/settings`에서 실제 값을 입력해야 바닥글에 표시됩니다.
- **상품 분류**: `V5__product_categories.sql`이 운영자가 관리하는 `product_categories` 테이블을 만들고, 기존 상품의 분류명(앞뒤 공백 정리)을 그대로 등록합니다. 상품은 계속 분류명을 저장하며, 분류 이름 변경·삭제 시 해당 상품의 분류명도 함께 바뀌거나 비워집니다.
- **업로드**: API 컨테이너는 UID 1000으로 실행하고 `uploads` 볼륨에 씁니다.
- **명령**: 기본 데이터 준비는 `docker compose run --rm api seed`, 체험 데이터는 `seed-demo --apply`입니다.

배포 전 PostgreSQL 덤프와 업로드 백업을 확보합니다.

## 체험 데이터

체험 데이터 생성은 시작 시 자동으로 실행되지 않습니다. 해당 원본 목상품이 있는 환경에서 백업을 확보한 후 필요할 때만 다음 명령을 실행합니다. 비밀번호는 12~128자이며 기존 `demo` 계정의 비밀번호는 재실행해도 바꾸지 않습니다. 다른 사람이 만든 `demo` 계정이 있으면 전체 작업을 취소합니다.

```bash
read -rs -p '체험 고객 비밀번호: ' DEMO_CUSTOMER_PASSWORD
export DEMO_CUSTOMER_PASSWORD
docker compose run --rm -T --no-deps -e DEMO_CUSTOMER_PASSWORD api seed-demo --apply
unset DEMO_CUSTOMER_PASSWORD
```

스크립트는 하나의 트랜잭션에서 실행합니다. 기존 목상품의 사진이 3장을 초과하면 삭제하지 않고 중단하며, 이후 등록한 상품에는 손대지 않습니다. 체험 주문 번호가 이미 있으면 중복 생성하거나 재고를 다시 차감하지 않고, 사용자가 체험 중 바꾼 주문 상태도 보존합니다.

## 최초 실행

```bash
cp .env.example .env
chmod 600 .env
# .env의 DB 비밀번호를 설정
bash scripts/deploy-server.sh
```

새 관리자 계정은 빈 DB에서 `SEED_ADMIN_USERNAME`, `SEED_ADMIN_PASSWORD`로 생성할 수 있습니다. 상품·고객·주문 목데이터는 자동으로 생성하지 않습니다. 초기 운영자·약관·운영 설정만 준비합니다.

## 도메인과 HTTPS

기존 Cloudflare Tunnel이 `http://localhost:3000`으로 전달하는 경우 Compose의 기본 웹 포트를 그대로 사용할 수 있습니다. `.env`의 `WEB_ORIGIN=https://damdamwood.co.kr`, `SESSION_COOKIE_SECURE=true`를 설정합니다. 외부 역방향 프록시는 원래 요청의 `X-Forwarded-Proto`를 전달해야 합니다. 브라우저는 같은 도메인의 `/api`를 사용합니다.

직접 외부에 웹 포트를 공개하려면 `WEB_BIND_HOST=0.0.0.0`을 지정하고 서버의 HTTPS 프록시를 구성합니다. API와 DB는 내부 또는 로컬 연결로 접근합니다.

## 운영 명령

```bash
docker compose ps
docker compose logs --tail=100 api web
docker compose restart api
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > backups/postgres.sql
```

백업 전 `mkdir -p backups`를 실행하고 접근 권한을 제한합니다. PostgreSQL(로그인 세션 포함)과 업로드 데이터는 이름 있는 볼륨에 남습니다. `docker compose down`은 볼륨을 유지하며 `down -v`는 데이터를 삭제하므로 데이터 초기화가 의도된 경우에만 사용합니다.

## 업데이트

```bash
git pull --ff-only
bash scripts/deploy-server.sh
```

업데이트 전 DB·업로드를 백업합니다. 스키마 변경이 포함된 버전의 롤백은 코드뿐 아니라 마이그레이션과 백업 복원 계획도 함께 검토합니다.
