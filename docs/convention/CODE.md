# 코드 컨벤션

상태: 적용. 작성일: 2026-10-07. 기준: ken-blog `docs/convention/CODE.md`(2026-10-06 Java 판).

Java API(`apps/api`)의 접근 범위·생성자 주입·JPA 엔티티·jOOQ·null 처리·API 사용 기준. 주석의 형식과 간격은 [주석 컨벤션](COMMENTS.md) 참조.

## 패키지 구조

- 업무 모듈은 `io.github.gjaku1031.damdam.<모듈>` 아래 `domain`(엔티티·enum·도메인 규칙), `repository`(Spring Data JPA), `service`, `controller`, `dto`로 나눔.
- 여러 모듈이 쓰는 웹·인증·오류·영속 기반은 `common`, 애플리케이션 설정은 `config`에 둠.
- 컨트롤러 경로는 `/api` 접두사를 붙이지 않고 작성. `WebConfig`가 모든 `@RestController`에 `/api`를 붙임.

## import와 타입 참조

- 외부 타입은 파일 상단의 `import`에 모으고 코드와 Javadoc에서 같은 단순 이름 사용. 필드·시그니처·어노테이션·메서드 본문에 전체 패키지 경로를 직접 쓰지 않음.
- Javadoc에서만 참조하는 타입도 같은 기준 적용. 같은 패키지·`java.lang` 타입은 import를 추가하지 않음.
- 단순 이름이 충돌하여 import로 구분할 수 없는 경우에만 전체 경로 사용.
- jOOQ 생성 테이블은 `import static ...jooq.Tables.TABLE_NAME` 형태로 참조.

## 접근 범위

- 선언의 접근 범위는 실제 호출자에게 필요한 수준으로 제한.
- 클래스 내부 상태·도우미는 `private`, 같은 패키지 안의 계약은 package-private, 다른 패키지에서 쓰는 계약만 `public`.
- 일반 클래스는 `final` 선언. 상속 계약이나 프레임워크 프록시가 필요한 경우에만 상속 허용.
  - `@Transactional` 메서드가 있는 서비스와 그 대상 메서드, `@Configuration` 클래스, JPA 엔티티·`@MappedSuperclass`는 `final`을 붙이지 않음.
  - 컨트롤러·`@RestControllerAdvice`·트랜잭션 없는 컴포넌트·유틸리티 클래스는 `final`.
- `protected`는 하위 클래스나 프록시에 공개할 멤버에만 사용. 상속 불가능한 일반 클래스에서 사용 금지.
- 불변 DTO는 `record` 사용. 필수 입력·기본값·직렬화 계약은 검증 어노테이션과 주석으로 명시.

## 생성자 주입

- 의존성을 그대로 대입하는 생성자는 직접 작성하지 않고 Lombok `@RequiredArgsConstructor` 사용. 주입 필드는 초기값 없는 `private final`.
- 생성자가 하나인 Spring 컴포넌트에 `@Autowired`를 추가하지 않음.
- 설정값으로 파생 객체를 만드는 등 입력 가공이 필요한 생성자만 직접 작성.
- Lombok은 `compileOnly`·`annotationProcessor`로 등록하고 버전은 Spring Boot BOM 사용.

## JPA 엔티티

### 상속 가능 범위

- `@Entity` 클래스와 `@MappedSuperclass`는 `final` 없이 선언. getter·setter·도메인 메서드에도 `final` 금지.
- 엔티티에 `record` 사용 금지.

### 생성자와 상태 변경

- JPA용 인자 없는 생성자는 `protected`(`@NoArgsConstructor(access = AccessLevel.PROTECTED)`).
- 생성은 의미 있는 정적 팩터리(`create`, `of` 등)로 제공. 빈 생성자로 만든 불완전한 인스턴스를 노출하지 않음.
- 필드는 `private`, 조회용 getter는 `public`. setter가 필요하면 `protected`로만 둠. `public` setter(`@Setter` 클래스 전체 적용 포함) 금지.
- 상태 변경은 업무 의미를 가진 `public` 도메인 메서드(`markPaid`, `cancel`, `hide` 등)로 수행하고 검증 규칙을 메서드 안에 둠.
- FK는 연관관계 대신 `UUID` 컬럼으로 매핑. 여러 테이블을 묶는 조회는 jOOQ로 수행.
- 식별자는 `BaseEntity`가 애플리케이션에서 생성. 외부에서 받은 ID로 저장할 때만 `assignId` 사용.

### 소프트 삭제

- `deletedAt`을 가진 엔티티의 조회는 `...AndDeletedAtIsNull` 메서드 또는 jOOQ 조건으로 삭제 행을 직접 제외. `@SQLRestriction` 같은 암묵 필터는 사용하지 않음.

## JPA와 jOOQ의 역할

- JPA: 엔티티 단위 생성·수정·삭제, ID 조회, 행 잠금(`@Lock(PESSIMISTIC_WRITE)`).
- jOOQ: 목록·검색·페이지·건수, 여러 테이블 `EXISTS`/조인, 벌크 갱신, `jsonb` 컬럼, 조건부(낙관적 revision) 갱신.
- 같은 트랜잭션에서 JPA로 쓴 내용을 jOOQ로 읽거나 갱신하면 그 전에 `flush()` 호출. jOOQ로 갱신한 행을 같은 영속성 컨텍스트에서 이미 읽었다면 다시 읽거나 엔티티로 갱신.
- jOOQ 코드는 Flyway 마이그레이션(`src/main/resources/db/migration`)에서 생성. 생성 코드는 직접 수정하지 않고 스키마 변경은 새 마이그레이션(`V2__...sql`)으로 추가.
- 이미 적용된 마이그레이션 파일은 수정하지 않음.

## 재고와 주문 잠금

- 재고 수량(`product_variants.stock`)을 바꾸는 모든 경로는 같은 트랜잭션에서 재고 이력(`inventory_adjustments`)을 함께 저장. 옵션별 이력 합계는 항상 현재 재고와 같아야 함. 새 옵션의 초기 재고도 `초기 재고` 이력으로 남김. 이 규칙이 깨진 옵션은 `InventoryService.ledgerMismatches`가 찾아 대시보드와 로그로 알림.
- 재고 변경 전에 옵션 행을 잠금(`findForUpdate`·`findAllForUpdate`). 여러 옵션은 ID 순서로 잠금.
- 주문 상태를 바꾸는 경로는 주문 행을 먼저 잠근 뒤 옵션 행을 잠금(주문 → 옵션). 잠근 뒤 상태·기한을 다시 확인하고 변경.
- 고객 주문 생성은 회원 행을 먼저 잠가 결제 대기 주문 수 제한을 확인한 뒤 옵션 행을 잠금(회원 → 옵션). 옵션을 잠근 뒤 회원 행을 잠그는 경로를 만들지 않음.
- 시간이 지나 실행되는 작업(입금 기한 자동 취소 등)은 대상마다 별도 트랜잭션으로 처리하고, 여러 번·여러 인스턴스에서 실행돼도 결과가 같게 작성.

## 사진 저장과 제공

- 사진 키(`product/<uuid>.jpg`, `/images/...`)는 원본을 가리키며 DB에는 이 키만 저장. 크기별 변환본(`ImageVariant`)은 `ImageKeys.variantKey`의 이름 규칙으로 찾고 DB에 목록을 두지 않음.
- 원본에는 촬영 위치 등 메타데이터가 남아 있으므로 변환본이 있는 사진의 원본을 응답으로 내보내지 않음. 원본 제공은 변환본이 없는 사진의 대체 경로에만 사용.
- 사진 변환(`ImageConverter`)은 DB 트랜잭션과 행 잠금 밖에서 수행. 변환이 끝난 뒤 짧은 트랜잭션에서 다시 확인하고 저장.
- 캐시 허용은 `StorageService`가 공개 사용처로 판정한 응답에만 적용. 운영자·본인 주문으로만 볼 수 있는 사진은 `no-store` 유지.
- 본문 HTML의 사진 주소에는 `size`를 붙이지 않음. 공개 여부 판정이 본문의 `src`를 그대로 비교하기 때문임.
- 사진 키를 저장하는 열·문서를 새로 만들면 `StorageService`의 사용처 조건(`referenced`)에 함께 추가. 이 조건에 없는 곳에서만 쓰이는 사진은 24시간 뒤 정리 작업이 지움.
- 업로드 사진을 상품·글에 연결하는 코드는 그 트랜잭션 안에서 `StorageService.assertUsable`을 호출. 티켓의 공유 잠금이 커밋까지 유지되어 정리 작업과 겹쳐도 연결한 사진이 지워지지 않음.

## 웹 계층과 오류

- 오류는 `ApiException`(상태·메시지·필드)으로 던지고 `GlobalExceptionHandler`가 `{success, statusCode, message, fields?, timestamp}`로 변환.
- 모든 응답의 기본 캐시 정책은 `no-store`(`NoStoreByDefaultFilter`). CDN은 캐시 지시가 없는 응답을 주소의 확장자만 보고 저장하고 쿠키를 구분하지 않으므로, 오류 응답과 JSON 응답에 이 기본값이 빠지면 안 됨.
- 캐시를 허용할 응답은 핸들러가 `ResponseEntity.cacheControl`로 명시. 로그인 여부·사용자에 따라 내용이 달라지는 응답에는 허용하지 않음.
- `POST` 생성·명령 핸들러는 `@ResponseStatus(HttpStatus.CREATED)`. 로그인·로그아웃처럼 200이 계약인 경우만 생략.
- 상태를 바꾸는 요청(`GET`·`HEAD`·`OPTIONS`·`TRACE`가 아닌 모든 컨트롤러 요청)은 `CsrfInterceptor`가 `X-CSRF-Token` 헤더를 세션의 토큰과 비교하고 다르면 403. 로그인·가입도 예외가 아님. 토큰은 `GET /api/auth/csrf`로 받으며 세션마다 하나이고, 로그인·로그아웃으로 세션이 바뀌면 다시 받아야 함.
- 웹은 `fetchApi`(`apps/web/lib/api.ts`)가 토큰을 받아 싣고, 403이면 토큰을 새로 받아 값이 달라졌을 때만 한 번 다시 보냄. API를 `fetchApi` 밖에서 직접 호출하는 코드를 만들지 않음.
- 로그인 필요 핸들러는 `@LoginRequired`, 운영자 전용은 `@AdminOnly`. `/api/admin/**`은 표시와 관계없이 운영자 검사. 현재 사용자는 `CurrentUser` 매개변수로 받음.
- 운영 컨트롤러(`/admin/**`)는 클래스에 `@AdminOnly`를 함께 붙임. 경로 검사와 표시 가운데 하나가 빠져도 다른 하나가 막도록 함.
- 권한·기록처럼 요청 경로로 판단하는 코드는 `RequestPaths.segments`를 사용. 원본 URI(`getRequestURI`)는 퍼센트 인코딩과 `;` 경로 매개변수가 남아 있어 핸들러 매핑과 결과가 달라지므로 판단에 쓰지 않음.
- 요청 본문은 `record` + Jakarta Validation. 선언하지 않은 필드·암묵 타입 변환은 거부됨(`JacksonConfig`).
- 쿼리 문자열의 페이지 값은 `Paging.of`로 해석.

## null 처리

- 타입·앞선 조건으로 non-null이 보장된 값에 불필요한 null 검사나 기본값을 추가하지 않음.
- 필수 입력, 생략 가능한 값, 명시적인 null을 구분. 부분 수정 DTO에서 `null`은 "바꾸지 않음"을 뜻하며 주석에 명시.
- 없을 수 있는 조회 결과는 `Optional`로 반환하고 `orElseThrow`로 오류를 명시.

## 지역 변수와 문자열

- 지역 변수는 사용하는 가장 좁은 범위에서 선언. 타입이 초기화 식에서 분명하면 `var` 사용 가능.
- 잠금·쓰기 순서를 표현하는 반복문을 기계적으로 스트림으로 바꾸지 않음.
- 로케일에 독립적인 식별자 정규화는 `Locale.ROOT` 사용.

## 라이브러리 API

- Spring Boot 4·Jackson 3 기준. Jackson 문자열 노드는 `isString`, `stringValue()`, 변환이 필요한 곳만 `asString()`.
- deprecated API는 공식 대체 API와 동작을 확인한 뒤 교체. 경고를 억제하는 방식으로 해결하지 않음.

## 검증

- 변경 후 `cd apps/api && ./gradlew --no-daemon compileJava`로 컴파일 경고·오류 확인.
- 엔티티·마이그레이션 변경 시 실제 PostgreSQL에서 기동해 Flyway와 Hibernate 스키마 검증(`ddl-auto: validate`) 통과 확인. 컴파일 성공만으로 영속 동작을 검증했다고 보고하지 않음.
