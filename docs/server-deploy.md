# Docker Compose 서버 운영

Docker Engine과 Compose v2가 설치된 서버에서 `gjaku1031/damdam` 저장소를 사용합니다.

## API 구성과 스키마 변경

API는 Spring Boot 4(Java 25)·JPA·jOOQ·Flyway로 구성하며 컨테이너 포트는 4000입니다.

- **스키마**: 시작 시 Flyway가 `V1__baseline.sql`부터 차례로 적용합니다. 스키마 변경은 새 `V<n>__*.sql`로 추가합니다.
- **세션**: 로그인 세션을 PostgreSQL(Spring Session JDBC, `V2__spring_session.sql`)에 저장합니다. API를 재시작해도 로그인이 유지됩니다. 로그인·가입 시도 제한은 API 메모리 카운터라 재시작하면 초기화됩니다.
- **결제**: 무통장 입금만 지원합니다.
- **배송 메모·사업자 정보**: `V4__order_ship_memo.sql`이 주문에 `shipMemo`(기본 빈 문자열)를 추가합니다. 시작 시 사업자 정보·고객센터 설정 키가 빈 값으로 준비되며, 배포 후 `/mypage/settings`에서 실제 값을 입력해야 바닥글에 표시됩니다.
- **상품 분류**: `V5__product_categories.sql`이 운영자가 관리하는 `product_categories` 테이블을 만들고, 기존 상품의 분류명(앞뒤 공백 정리)을 그대로 등록합니다. 상품은 계속 분류명을 저장하며, 분류 이름 변경·삭제 시 해당 상품의 분류명도 함께 바뀌거나 비워집니다.
- **입금 기한·재고 보호**: `V6__payment_deadline_and_stock_guards.sql`이 주문에 `paymentDueAt`(입금 기한)을 추가하고, 이미 결제 대기 중인 주문에는 적용일 + 2일 23:59(한국 시간)를 기한으로 줍니다. 옵션 재고에 `CHECK (stock >= 0)`을 걸고, 옵션마다 재고 이력 합계와 현재 재고의 차이를 `이력 보정` 한 건으로 기록하며, 쓰지 않는 `inventory_reservations` 테이블을 삭제합니다. 음수 재고나 활성 예약이 있으면 아무것도 바꾸지 않고 시작을 중단합니다. 적용 후에는 5분마다 기한이 지난 결제 대기 주문이 자동 취소되고 재고가 돌아옵니다. 기한 일수는 `/mypage/settings`의 입금 안내에서 바꿉니다(기본 2일). V6 이전 API 이미지로 되돌리려면 `inventory_reservations` 테이블을 V1의 정의로 다시 만들어야 합니다.
- **업로드**: API 컨테이너는 UID 1000으로 실행하고 `uploads` 볼륨에 씁니다.
- **사진 변환본**: 사진을 올릴 때 API가 원본과 함께 가로 400·800·1600px WebP 세 벌을 `uploads` 볼륨에 저장합니다(`product/<id>-s.webp`, `-m`, `-l`). 변환은 API 이미지에 설치한 libvips(`vips` 명령)가 하며, 이 때문에 API 이미지가 약 160MB 커집니다. 사진 주소에 `?size=s|m|l`을 붙이면 해당 벌을, 생략하면 큰 벌을 보냅니다. 변환본이 있는 사진의 원본은 내보내지 않습니다. 공개된 상품·글·공지에 쓰인 사진은 `Cache-Control: public, max-age=86400`으로 응답하므로, 상품이나 글을 숨겨도 이미 받아 간 브라우저·CDN에는 최대 하루 남습니다. 이 버전을 처음 배포한 뒤에는 `docker compose run --rm api optimize-images`를 한 번 실행해 그 전에 올린 사진과 기존 사진(`/images/...`, 변환본은 `uploads` 볼륨의 `legacy/` 아래)에 변환본을 만듭니다. 실행 전에도 사진은 원본으로 정상 표시되며, 이미 변환한 사진은 건너뛰므로 여러 번 실행해도 됩니다. 스키마 변경은 없고 이전 이미지로 되돌려도 변환본 파일만 쓰이지 않은 채 남습니다.
- **응답 캐시**: API는 모든 응답에 `Cache-Control: no-store`를 기본으로 붙이고, 공개 사진 응답만 `public, max-age=86400`으로 바꿉니다. 운영 앞단의 Cloudflare는 캐시 지시가 없는 응답을 주소의 확장자만 보고 저장하므로(`/api/orders/me;a.jpg`처럼 `;` 뒤에 확장자를 붙이면 JSON 응답도 저장됨) 이 기본값이 빠지면 안 됩니다. 배포 후 `curl -sI 'https://<도메인>/api/products;check.jpg'`의 응답이 `cache-control: no-store`이고 `cf-cache-status`가 `HIT`이 아닌지 확인합니다. 선택 근거는 `docs/response-cache-tradeoffs.md`에 있습니다.
- **명령**: 기본 데이터 준비는 `docker compose run --rm api seed`, 체험 데이터는 `seed-demo --apply`, 사진 변환본 일괄 생성은 `optimize-images`입니다.

배포 전 PostgreSQL 덤프와 업로드 백업을 확보합니다.

## 체험 데이터

체험 데이터 생성은 시작 시 자동으로 실행되지 않습니다. 해당 원본 목상품이 있는 환경에서 백업을 확보한 후 필요할 때만 다음 명령을 실행합니다. 비밀번호는 영문과 숫자를 포함한 8~128자이며 기존 `demo` 계정의 비밀번호는 재실행해도 바꾸지 않습니다. 다른 사람이 만든 `demo` 계정이 있으면 전체 작업을 취소합니다.

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
