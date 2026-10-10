package io.github.gjaku1031.damdam.seed;

import static io.github.gjaku1031.damdam.jooq.Tables.INVENTORY_ADJUSTMENTS;
import static io.github.gjaku1031.damdam.jooq.Tables.ORDERS;
import static io.github.gjaku1031.damdam.jooq.Tables.ORDER_ITEMS;
import static io.github.gjaku1031.damdam.jooq.Tables.ORDER_STATUS_HISTORIES;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_IMAGES;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_VARIANTS;
import static io.github.gjaku1031.damdam.jooq.Tables.USERS;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.auth.service.PasswordHasher;
import io.github.gjaku1031.damdam.jooq.tables.records.ProductImagesRecord;
import io.github.gjaku1031.damdam.jooq.tables.records.ProductVariantsRecord;
import io.github.gjaku1031.damdam.jooq.tables.records.ProductsRecord;
import io.github.gjaku1031.damdam.jooq.tables.records.UsersRecord;
import io.github.gjaku1031.damdam.order.domain.ChangeActorType;
import io.github.gjaku1031.damdam.order.domain.OrderItemStatus;
import io.github.gjaku1031.damdam.order.domain.OrderPaymentMethod;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import io.github.gjaku1031.damdam.system.domain.PaymentDeadlinePolicy;
import io.github.gjaku1031.damdam.user.domain.UserRole;
import lombok.RequiredArgsConstructor;

/**
 * 체험용 주문 흐름 데이터 생성({@code seed-demo --apply} 명령 전용)
 *
 * 애플리케이션 시작·마이그레이션에서는 실행하지 않음
 * 지정된 기존 목상품 16개에만 사진을 보충하고, 체험 고객 {@code demo}에 상태별 주문 7건을 만듦
 * 재실행해도 이미 만든 주문·사진·비밀번호를 바꾸지 않음
 */
@Service
@RequiredArgsConstructor
public class DemoDataSeeder {
    /**
     * 사진을 보충할 목상품 ID와 후보 사진 번호({@code /images/product-N.jpg})
     */
    private static final Map<UUID, int[]> DEMO_CATALOG = catalog();

    /**
     * 체험 고객 ID
     */
    public static final UUID DEMO_CUSTOMER_ID = UUID.fromString("05eb2a4c-8d4f-42bf-9e52-871376954df9");

    /**
     * 체험 고객 아이디
     */
    private static final String DEMO_USERNAME = "demo";

    /**
     * 주문별 상태 진행 순서, 마지막 값이 현재 상태
     */
    private static final List<List<OrderStatus>> STAGES = List.of(
            List.of(OrderStatus.PENDING_PAYMENT),
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID),
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PROCESSING),
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED),
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELED),
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PARTIALLY_REFUNDED),
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.FULLY_REFUNDED));

    /**
     * 상태별 이력 사유
     */
    private static final Map<OrderStatus, String> STAGE_REASONS = Map.of(
            OrderStatus.PENDING_PAYMENT, "체험용 주문 접수 · 입금 대기",
            OrderStatus.PAID, "체험용 무통장 입금 확인",
            OrderStatus.PROCESSING, "체험용 상품 준비 및 배송 시작",
            OrderStatus.COMPLETED, "체험용 배송 완료",
            OrderStatus.CANCELED, "체험용 고객 요청 취소 · 재고 복원",
            OrderStatus.PARTIALLY_REFUNDED, "체험용 일부 금액 환불",
            OrderStatus.FULLY_REFUNDED, "체험용 전액 환불");

    /**
     * 상품당 맞출 사진 장수
     */
    private static final int PHOTOS_PER_PRODUCT = 3;

    /**
     * 체험 데이터 jOOQ 접근
     */
    private final DSLContext dsl;

    /**
     * 체험 고객 비밀번호 해시
     */
    private final PasswordHasher passwords;

    /**
     * 전체 작업을 한 트랜잭션으로 묶는 실행기
     */
    private final TransactionTemplate transaction;

    /**
     * 체험 데이터 생성
     *
     * 하나의 트랜잭션에서 실행, 오류가 나면 어떤 데이터도 바뀌지 않음
     *
     * 1. 동시 실행 방지용 권고 잠금, 목상품 행 잠금
     * 2. 목상품 사진을 3장까지 보충(3장 초과 상품이 있으면 중단)
     * 3. 체험 고객 확인 또는 생성(다른 사람의 {@code demo} 계정이면 중단)
     * 4. 판매 중 옵션을 잠근 뒤 없는 체험 주문만 생성, 재고·재고 이력 반영
     *
     * @param password 체험 고객 비밀번호, 12~128자(기존 계정이면 사용하지 않음)
     * @return 고객 아이디, 대상 상품 수, 추가한 사진 수, 생성한 주문 수
     * @throws IllegalArgumentException 비밀번호 길이가 잘못됐을 때
     * @throws IllegalStateException 목상품이 없거나 사진·계정·재고 조건이 맞지 않을 때
     */
    public Map<String, Object> seed(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Set DEMO_CUSTOMER_PASSWORD to 12–128 characters.");
        }
        String passwordHash = passwords.hash(password);
        return transaction.execute(status -> seedInTransaction(passwordHash));
    }

    /**
     * 트랜잭션 안의 체험 데이터 생성
     *
     * @return 실행 결과 요약
     */
    private Map<String, Object> seedInTransaction(String passwordHash) {
        dsl.execute("SELECT pg_advisory_xact_lock(hashtext('damdam:demo-20261003'))");

        // 삭제되지 않은 목상품만 대상, ID 순서로 잠금
        List<ProductsRecord> products = dsl.selectFrom(PRODUCTS)
                .where(PRODUCTS.ID.in(DEMO_CATALOG.keySet()), PRODUCTS.DELETEDAT.isNull())
                .orderBy(PRODUCTS.ID).forUpdate().fetch();
        if (products.isEmpty()) throw new IllegalStateException("The legacy mock catalog is missing; no data was changed.");
        int addedPhotos = 0;
        for (ProductsRecord product : products) addedPhotos += fillPhotos(product.getId());

        UUID customerId = ensureCustomer(passwordHash);

        // 공개 목상품의 판매 중 옵션을 잠근 뒤 최신 재고로 주문 생성
        List<UUID> visibleIds = products.stream().filter(ProductsRecord::getVisible).map(ProductsRecord::getId).toList();
        List<ProductVariantsRecord> variants = dsl.selectFrom(PRODUCT_VARIANTS)
                .where(PRODUCT_VARIANTS.PRODUCTID.in(visibleIds), PRODUCT_VARIANTS.ACTIVE.isTrue())
                .orderBy(PRODUCT_VARIANTS.ID).forUpdate().fetch();
        Map<UUID, String> productNames = new LinkedHashMap<>();
        products.forEach(product -> productNames.put(product.getId(), product.getName()));
        Instant now = Instant.now();
        int createdOrders = 0;
        for (int index = 0; index < STAGES.size(); index++) {
            if (createOrder(index, customerId, variants, productNames, now)) createdOrders++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", DEMO_USERNAME);
        result.put("products", products.size());
        result.put("addedPhotos", addedPhotos);
        result.put("createdOrders", createdOrders);
        return result;
    }

    /**
     * 목상품 사진을 3장까지 보충
     *
     * 운영자가 추가한 사진을 지우지 않도록 3장 초과면 중단
     *
     * @return 추가한 사진 수
     * @throws IllegalStateException 사진이 3장을 넘거나 3장을 맞출 수 없을 때
     */
    private int fillPhotos(UUID productId) {
        List<ProductImagesRecord> images = dsl.selectFrom(PRODUCT_IMAGES).where(PRODUCT_IMAGES.PRODUCTID.eq(productId))
                .orderBy(PRODUCT_IMAGES.SORTORDER).fetch();
        if (images.size() > PHOTOS_PER_PRODUCT) {
            throw new IllegalStateException("Mock product " + productId + " has more than three photos; review its changes first.");
        }
        List<String> keys = new ArrayList<>(images.stream().map(ProductImagesRecord::getObjectkey).toList());
        int nextSort = images.stream().mapToInt(ProductImagesRecord::getSortorder).max().orElse(-1) + 1;
        int added = 0;
        for (int candidate : DEMO_CATALOG.get(productId)) {
            String key = "/images/product-" + candidate + ".jpg";
            if (keys.size() >= PHOTOS_PER_PRODUCT) break;
            if (keys.contains(key)) continue;
            Instant now = Instant.now();
            dsl.insertInto(PRODUCT_IMAGES)
                    .set(PRODUCT_IMAGES.ID, UUID.randomUUID())
                    .set(PRODUCT_IMAGES.CREATEDAT, now)
                    .set(PRODUCT_IMAGES.UPDATEDAT, now)
                    .set(PRODUCT_IMAGES.PRODUCTID, productId)
                    .set(PRODUCT_IMAGES.OBJECTKEY, key)
                    .set(PRODUCT_IMAGES.SORTORDER, nextSort++)
                    .execute();
            keys.add(key);
            added++;
        }
        if (keys.size() != PHOTOS_PER_PRODUCT) {
            throw new IllegalStateException("Could not prepare three photos for " + productId + ".");
        }
        return added;
    }

    /**
     * 체험 고객 확인 또는 생성
     *
     * @return 체험 고객 ID
     * @throws IllegalStateException {@code demo} 아이디나 체험 고객 ID가 다른 계정에 쓰이고 있을 때
     */
    private UUID ensureCustomer(String passwordHash) {
        UsersRecord existing = dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(DEMO_USERNAME).or(USERS.ID.eq(DEMO_CUSTOMER_ID)))
                .fetchAny();
        if (existing != null) {
            boolean ours = existing.getId().equals(DEMO_CUSTOMER_ID) && DEMO_USERNAME.equals(existing.getUsername())
                    && UserRole.CUSTOMER.name().equals(existing.getRole()) && existing.getDeletedat() == null;
            if (!ours) {
                throw new IllegalStateException(
                        "The demo username is already in use; no existing account will be overwritten.");
            }
            return existing.getId();
        }
        Instant now = Instant.now();
        dsl.insertInto(USERS)
                .set(USERS.ID, DEMO_CUSTOMER_ID)
                .set(USERS.CREATEDAT, now)
                .set(USERS.UPDATEDAT, now)
                .set(USERS.USERNAME, DEMO_USERNAME)
                .set(USERS.ROLE, UserRole.CUSTOMER.name())
                .set(USERS.PASSWORDHASH, passwordHash)
                .execute();
        return DEMO_CUSTOMER_ID;
    }

    /**
     * 체험 주문 하나 생성
     *
     * 주문번호가 이미 있으면 사용자가 체험 중 바꾼 상태를 보존하기 위해 건너뜀
     * 생성 시각은 주문마다 하루씩 과거로, 상태 단계는 15분 간격으로 기록
     * 결제 대기 주문의 입금 기한은 생성 시각이 아니라 체험 데이터를 만든 시각 기준(만들자마자 자동 취소되지 않게 함)
     *
     * @param variants 잠근 판매 중 옵션, 재고 차감을 반영해 갱신
     * @return 새로 만들었으면 {@code true}
     * @throws IllegalStateException 주문번호가 다른 고객의 것이거나 재고 2개 이상인 옵션이 없을 때
     */
    private boolean createOrder(int index, UUID customerId, List<ProductVariantsRecord> variants,
            Map<UUID, String> productNames, Instant now) {
        String orderNo = "DEMO-20261003-%02d".formatted(index + 1);
        var existing = dsl.select(ORDERS.USERID).from(ORDERS).where(ORDERS.ORDERNO.eq(orderNo)).fetchOptional();
        if (existing.isPresent()) {
            if (!customerId.equals(existing.get().value1())) throw new IllegalStateException("Demo order number belongs to another customer.");
            return false;
        }
        List<ProductVariantsRecord> available = variants.stream().filter(variant -> variant.getStock() >= 2).toList();
        if (available.isEmpty()) throw new IllegalStateException("Insufficient mock catalog stock; no data was changed.");

        // 주문 내용과 단계별 시각
        List<OrderStatus> history = STAGES.get(index);
        ProductVariantsRecord variant = available.get(index % available.size());
        int quantity = index == 3 ? 2 : 1;
        Instant createdAt = now.minus(Duration.ofMinutes(index * 24L * 60 + 90));
        OrderStatus status = history.getLast();
        Instant updatedAt = at(createdAt, history.size() - 1);
        int total = variant.getPriceamount() * quantity;
        Instant paidAt = history.contains(OrderStatus.PAID) ? at(createdAt, 1) : null;
        Instant canceledAt = status == OrderStatus.CANCELED ? updatedAt : null;
        boolean refunded = status == OrderStatus.FULLY_REFUNDED || status == OrderStatus.PARTIALLY_REFUNDED;
        int refundedAmount = status == OrderStatus.FULLY_REFUNDED ? total
                : status == OrderStatus.PARTIALLY_REFUNDED ? total / 2 : 0;

        // 무통장 입금 주문 저장
        UUID orderId = UUID.randomUUID();
        dsl.insertInto(ORDERS)
                .set(ORDERS.ID, orderId)
                .set(ORDERS.CREATEDAT, createdAt)
                .set(ORDERS.UPDATEDAT, updatedAt)
                .set(ORDERS.ORDERNO, orderNo)
                .set(ORDERS.USERID, customerId)
                .set(ORDERS.STATUS, status.name())
                .set(ORDERS.PAYMENTMETHOD, OrderPaymentMethod.BANK_TRANSFER.name())
                .set(ORDERS.TOTALAMOUNT, total)
                .set(ORDERS.SHIPPINGCOST, 0)
                .set(ORDERS.FINALPAYMENTAMOUNT, total)
                .set(ORDERS.REFUNDEDAMOUNT, refundedAmount)
                .set(ORDERS.CURRENCY, "KRW")
                .set(ORDERS.BUYERNAME, "체험 고객")
                .set(ORDERS.BUYERPHONE, "010-0000-0000")
                .set(ORDERS.SHIPNAME, "체험 고객")
                .set(ORDERS.SHIPPHONE, "010-0000-0000")
                .set(ORDERS.SHIPPOSTALCODE, "00000")
                .set(ORDERS.SHIPADDRESS1, "체험용 배송지 (실제 배송 없음)")
                .set(ORDERS.SHIPADDRESS2, "담담공방 주문 흐름 확인용")
                .set(ORDERS.PAYMENTDUEAT, PaymentDeadlinePolicy.dueAt(
                        status == OrderStatus.PENDING_PAYMENT ? now : createdAt, PaymentDeadlinePolicy.DEFAULT_DAYS))
                .set(ORDERS.PAIDAT, paidAt)
                .set(ORDERS.CANCELEDAT, canceledAt)
                .set(ORDERS.REFUNDEDAT, refunded ? updatedAt : null)
                .execute();

        // 대표 사진·가격 스냅샷을 담은 주문 상품
        String thumbnail = dsl.select(PRODUCT_IMAGES.OBJECTKEY).from(PRODUCT_IMAGES)
                .where(PRODUCT_IMAGES.PRODUCTID.eq(variant.getProductid()))
                .orderBy(PRODUCT_IMAGES.SORTORDER).limit(1).fetchOne(PRODUCT_IMAGES.OBJECTKEY);
        dsl.insertInto(ORDER_ITEMS)
                .set(ORDER_ITEMS.ID, UUID.randomUUID())
                .set(ORDER_ITEMS.CREATEDAT, createdAt)
                .set(ORDER_ITEMS.UPDATEDAT, updatedAt)
                .set(ORDER_ITEMS.ORDERID, orderId)
                .set(ORDER_ITEMS.VARIANTID, variant.getId())
                .set(ORDER_ITEMS.PRODUCTNAMESNAPSHOT, productNames.get(variant.getProductid()))
                .set(ORDER_ITEMS.VARIANTNAMESNAPSHOT, variant.getName())
                .set(ORDER_ITEMS.THUMBNAILIMAGEURLSNAPSHOT, thumbnail)
                .set(ORDER_ITEMS.UNITPRICEAMOUNT, variant.getPriceamount())
                .set(ORDER_ITEMS.QUANTITY, quantity)
                .set(ORDER_ITEMS.STATUS, itemStatus(status).name())
                .set(ORDER_ITEMS.PAIDAT, paidAt)
                .set(ORDER_ITEMS.SHIPPEDAT, history.contains(OrderStatus.PROCESSING) ? at(createdAt, 2) : null)
                .set(ORDER_ITEMS.CANCELEDAT, canceledAt)
                .set(ORDER_ITEMS.REFUNDEDAT, refunded ? updatedAt : null)
                .execute();

        // 단계별 상태 이력, 첫 단계는 고객·이후는 운영자
        for (int step = 0; step < history.size(); step++) {
            dsl.insertInto(ORDER_STATUS_HISTORIES)
                    .set(ORDER_STATUS_HISTORIES.ID, UUID.randomUUID())
                    .set(ORDER_STATUS_HISTORIES.CREATEDAT, at(createdAt, step))
                    .set(ORDER_STATUS_HISTORIES.UPDATEDAT, at(createdAt, step))
                    .set(ORDER_STATUS_HISTORIES.ORDERID, orderId)
                    .set(ORDER_STATUS_HISTORIES.FROMSTATUS, step == 0 ? null : history.get(step - 1).name())
                    .set(ORDER_STATUS_HISTORIES.TOSTATUS, history.get(step).name())
                    .set(ORDER_STATUS_HISTORIES.CHANGEDBY,
                            (step == 0 ? ChangeActorType.CUSTOMER : ChangeActorType.ADMIN).name())
                    .set(ORDER_STATUS_HISTORIES.REASON, STAGE_REASONS.get(history.get(step)))
                    .execute();
        }

        // 재고: 취소 주문은 차감·복원 이력만, 나머지는 실제 재고 차감
        adjustment(variant.getId(), customerId, -quantity, "체험용 주문 " + orderNo, createdAt);
        if (status == OrderStatus.CANCELED) {
            adjustment(variant.getId(), customerId, quantity, "체험용 주문 취소 " + orderNo, canceledAt);
        } else {
            variant.setStock(variant.getStock() - quantity);
            dsl.update(PRODUCT_VARIANTS).set(PRODUCT_VARIANTS.STOCK, variant.getStock())
                    .where(PRODUCT_VARIANTS.ID.eq(variant.getId())).execute();
        }
        return true;
    }

    /**
     * 재고 이력 기록
     */
    private void adjustment(UUID variantId, UUID actorUserId, int delta, String reason, Instant createdAt) {
        dsl.insertInto(INVENTORY_ADJUSTMENTS)
                .set(INVENTORY_ADJUSTMENTS.ID, UUID.randomUUID())
                .set(INVENTORY_ADJUSTMENTS.CREATEDAT, createdAt)
                .set(INVENTORY_ADJUSTMENTS.UPDATEDAT, createdAt)
                .set(INVENTORY_ADJUSTMENTS.VARIANT_ID, variantId)
                .set(INVENTORY_ADJUSTMENTS.ACTOR_USER_ID, actorUserId)
                .set(INVENTORY_ADJUSTMENTS.DELTA, delta)
                .set(INVENTORY_ADJUSTMENTS.REASON, reason)
                .execute();
    }

    /**
     * 주문 상태에 대응하는 주문 상품 상태
     *
     * @return 일부 환불은 결제 완료 유지
     */
    private static OrderItemStatus itemStatus(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> OrderItemStatus.PENDING_PAYMENT;
            case PROCESSING -> OrderItemStatus.SHIPPED;
            case COMPLETED -> OrderItemStatus.DELIVERED;
            case CANCELED -> OrderItemStatus.CANCELED;
            case FULLY_REFUNDED -> OrderItemStatus.REFUNDED;
            default -> OrderItemStatus.PAID;
        };
    }

    /**
     * 단계 시각
     *
     * @return 생성 시각부터 단계마다 15분 뒤
     */
    private static Instant at(Instant createdAt, int stage) {
        return createdAt.plus(Duration.ofMinutes(stage * 15L));
    }

    /**
     * 목상품 목록 구성
     *
     * @return 입력 순서를 유지한 상품 ID별 후보 사진 번호
     */
    private static Map<UUID, int[]> catalog() {
        Object[][] rows = {
                {"5a6afa6b-bcbd-4923-94a9-6a81b7ec8cd3", new int[] {1, 4, 11}},
                {"f971e0b6-2c61-4df7-ad7f-a289f0c60892", new int[] {2, 14, 6}},
                {"1b49126d-df03-4f9e-ae3f-5ea8b915f636", new int[] {3, 9, 14}},
                {"43f6a6c1-342f-4268-85bf-cfa52c2752db", new int[] {4, 1, 8}},
                {"df1533dc-cac7-46c9-9307-227836af58e6", new int[] {5, 10, 7}},
                {"16954208-69c8-4dc2-9f2c-40fdb0ea7ff4", new int[] {6, 2, 14}},
                {"a1022536-d9fd-4db1-9719-5765fb4f9cf4", new int[] {7, 10, 5}},
                {"64e46221-33d2-429a-bec5-16ec6811b652", new int[] {8, 1, 4}},
                {"d41a8bb0-fadc-4a95-9587-94b2abee302b", new int[] {9, 3, 14}},
                {"de5ebbcf-9a53-468e-93be-39d9cbbf1aff", new int[] {10, 7, 5}},
                {"df72bae9-5f3a-4568-a645-6977682d4b40", new int[] {11, 4, 8}},
                {"9756b5de-2c08-4545-ac9a-163d064c2072", new int[] {12, 2, 14}},
                {"e4c9dfe7-16ba-4f23-b238-53ffd3f69622", new int[] {13, 7, 10}},
                {"55214233-70a1-4379-a69b-9d6b9d28eb13", new int[] {14, 6, 2}},
                {"a562a3aa-975b-4f41-b440-5644f1d223a9", new int[] {2, 6, 14}},
                {"479409a4-6f5f-4137-9ca8-1499f96c8db9", new int[] {10, 7, 5}}};
        Map<UUID, int[]> catalog = new LinkedHashMap<>();
        for (Object[] row : rows) catalog.put(UUID.fromString((String) row[0]), (int[]) row[1]);
        return catalog;
    }
}
