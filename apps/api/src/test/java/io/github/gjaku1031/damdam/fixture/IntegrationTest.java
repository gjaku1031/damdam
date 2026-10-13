package io.github.gjaku1031.damdam.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 서버와 PostgreSQL로 실행하는 통합 테스트의 공통 기반
 *
 * 모든 통합 테스트가 같은 설정을 쓰므로 Spring이 서버와 DB 컨테이너를 한 번만 띄워 함께 사용
 * DB를 테스트마다 비우지 않으므로, 각 테스트는 자기가 만든 데이터(고유한 아이디·상품)만으로 판단해야 함
 * 스케줄러는 끄고, 시간이 지나 실행되는 작업은 테스트가 서비스를 직접 호출해 검증
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"damdam.scheduling.enabled=false", "damdam.trust-proxy=true",
                "damdam.web-origin=" + ApiClient.ORIGIN,
                "damdam.seed.admin-username=" + IntegrationTest.ADMIN_USERNAME,
                "damdam.seed.admin-password=" + IntegrationTest.ADMIN_PASSWORD})
@Import(TestPostgresConfig.class)
public abstract class IntegrationTest {
    /**
     * 시작할 때 만들어지는 운영자 아이디
     */
    protected static final String ADMIN_USERNAME = "admin";

    /**
     * 운영자 비밀번호, 테스트 전용 값
     */
    protected static final String ADMIN_PASSWORD = "operator-pass-1";

    /**
     * 테스트 회원의 비밀번호, 테스트 전용 값
     */
    protected static final String MEMBER_PASSWORD = "member-pass-1";

    /**
     * 사진 형식 검사를 통과하는 1×1 PNG
     */
    protected static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    /**
     * 업로드 파일을 저장하는 임시 디렉터리, 모든 통합 테스트가 함께 사용
     */
    protected static final Path UPLOADS = createUploads();

    /**
     * 테스트 서버 포트
     */
    @LocalServerPort
    protected int port;

    /**
     * 테스트 데이터 준비와 DB 상태 확인용 JDBC 실행기
     */
    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * JSON 직렬화기
     */
    @Autowired
    protected ObjectMapper json;

    /**
     * 업로드 저장 경로를 임시 디렉터리로 지정
     */
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("damdam.storage.local-path", UPLOADS::toString);
        registry.add("damdam.storage.legacy-images-path", () -> UPLOADS.resolve("legacy-source").toString());
    }

    /**
     * 로그인하지 않은 새 브라우저
     */
    protected ApiClient visitor() {
        return new ApiClient(port, json);
    }

    /**
     * 운영자로 로그인한 새 브라우저
     */
    protected ApiClient admin() {
        ApiClient client = visitor();
        assertThat(client.login(ADMIN_USERNAME, ADMIN_PASSWORD).status()).isEqualTo(200);
        return client;
    }

    /**
     * 새 회원으로 가입해 로그인된 브라우저
     */
    protected ApiClient member() {
        return member(uniqueName());
    }

    /**
     * 지정한 아이디로 가입해 로그인된 브라우저
     */
    protected ApiClient member(String username) {
        ApiClient client = visitor();
        created(client.post("/auth/register", Map.of("username", username, "password", MEMBER_PASSWORD,
                "serviceTermsAccepted", true, "privacyTermsAccepted", true)));
        return client;
    }

    /**
     * 가입 규칙(영문 소문자·숫자·밑줄 4~20자)에 맞는 겹치지 않는 아이디
     */
    protected static String uniqueName() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
    }

    /**
     * 옵션 하나짜리 공개 상품 등록
     *
     * @param stock 초기 재고
     * @param imageKeys 연결할 사진 키
     * @return 등록한 상품 응답
     */
    protected JsonNode product(ApiClient admin, int stock, String... imageKeys) {
        return product(admin, stock, true, imageKeys);
    }

    /**
     * 옵션 하나짜리 상품 등록
     *
     * @param stock 초기 재고
     * @param visible 공개 여부
     * @param imageKeys 연결할 사진 키
     * @return 등록한 상품 응답
     */
    protected JsonNode product(ApiClient admin, int stock, boolean visible, String... imageKeys) {
        List<Map<String, String>> images = new ArrayList<>();
        for (String key : imageKeys) images.add(Map.of("objectKey", key));
        return created(admin.post("/admin/products", Map.of(
                "name", "테스트 상품 " + uniqueName(),
                "visible", visible,
                "images", images,
                "variants", List.of(Map.of("name", "기본", "priceAmount", 1000, "stock", stock, "isDefault", true,
                        "active", true))))).body();
    }

    /**
     * 상품의 첫 옵션 ID
     */
    protected static String variantId(JsonNode product) {
        return product.path("variants").path(0).path("id").asString();
    }

    /**
     * 옵션 하나를 주문하는 요청 본문
     */
    protected static Map<String, Object> orderOf(String variantId, int quantity) {
        return Map.of("items", List.of(Map.of("variantId", variantId, "quantity", quantity)),
                "buyerName", "주문자", "buyerPhone", "010-0000-0000", "shipName", "받는이", "shipPhone", "010-0000-0000",
                "shipPostalCode", "00000", "shipAddress1", "테스트 주소");
    }

    /**
     * 사진 한 장을 올려 검증 완료된 업로드로 만듦
     *
     * @param domain {@code PRODUCT} 또는 {@code POST}
     * @return 저장 키
     */
    protected String uploadPhoto(ApiClient admin, String domain) {
        JsonNode ticket = created(admin.post("/storage/tickets", Map.of("domain", domain, "contentType", "image/png",
                "sizeBytes", PNG.length))).body();
        return created(admin.upload(ticket.path("id").asString(), PNG, "image/png")).body().path("objectKey").asString();
    }

    /**
     * 현재 DB의 옵션 재고
     */
    protected int stockOf(String variantId) {
        return jdbc.queryForObject("select \"stock\" from \"product_variants\" where \"id\" = ?::uuid", Integer.class,
                variantId);
    }

    /**
     * 옵션의 재고 이력 변화량 합계
     */
    protected int ledgerOf(String variantId) {
        return jdbc.queryForObject("select coalesce(sum(\"delta\"), 0) from \"inventory_adjustments\" "
                + "where \"variant_id\" = ?::uuid", Integer.class, variantId);
    }

    /**
     * 200 응답 확인
     *
     * @return 확인한 응답
     */
    protected static ApiResponse ok(ApiResponse response) {
        assertThat(response.status()).as(response.message()).isEqualTo(200);
        return response;
    }

    /**
     * 201 응답 확인
     *
     * @return 확인한 응답
     */
    protected static ApiResponse created(ApiResponse response) {
        assertThat(response.status()).as(response.message()).isEqualTo(201);
        return response;
    }

    /**
     * 업로드 임시 디렉터리 생성
     */
    private static Path createUploads() {
        try {
            return Files.createTempDirectory("damdam-test-uploads");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
