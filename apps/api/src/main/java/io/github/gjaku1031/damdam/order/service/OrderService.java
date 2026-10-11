package io.github.gjaku1031.damdam.order.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ORDERS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_IMAGES;
import static io.github.gjaku1031.damdam.jooq.Tables.USERS;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.inventory.domain.InventoryAdjustmentEntity;
import io.github.gjaku1031.damdam.inventory.repository.InventoryAdjustmentRepository;
import io.github.gjaku1031.damdam.order.domain.ChangeActorType;
import io.github.gjaku1031.damdam.order.domain.OrderContact;
import io.github.gjaku1031.damdam.order.domain.OrderEntity;
import io.github.gjaku1031.damdam.order.domain.OrderItemEntity;
import io.github.gjaku1031.damdam.order.domain.OrderPaymentMethod;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import io.github.gjaku1031.damdam.order.domain.OrderStatusHistoryEntity;
import io.github.gjaku1031.damdam.order.domain.OrderTransitions;
import io.github.gjaku1031.damdam.order.dto.CreateOrderRequest;
import io.github.gjaku1031.damdam.order.dto.CreateProxyOrderRequest;
import io.github.gjaku1031.damdam.order.dto.OrderItemResponse;
import io.github.gjaku1031.damdam.order.dto.OrderPage;
import io.github.gjaku1031.damdam.order.dto.OrderResponse;
import io.github.gjaku1031.damdam.order.dto.OrderStatusHistoryResponse;
import io.github.gjaku1031.damdam.order.repository.OrderItemRepository;
import io.github.gjaku1031.damdam.order.repository.OrderRepository;
import io.github.gjaku1031.damdam.order.repository.OrderStatusHistoryRepository;
import io.github.gjaku1031.damdam.product.domain.ProductEntity;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import io.github.gjaku1031.damdam.product.dto.ProductVariantResponse;
import io.github.gjaku1031.damdam.product.repository.ProductRepository;
import io.github.gjaku1031.damdam.product.repository.ProductVariantRepository;
import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;
import io.github.gjaku1031.damdam.system.domain.PaymentDeadlinePolicy;
import io.github.gjaku1031.damdam.system.service.SystemService;
import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.dto.UserResponse;
import io.github.gjaku1031.damdam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 주문 생성·조회·상태 전이
 *
 * 주문 생성과 취소는 옵션 행을 ID 순서로 잠근 뒤 재고 변경·재고 이력을 한 트랜잭션에서 처리
 * 동시 주문이 재고를 초과하지 않고, 실패 시 주문·재고·이력이 함께 롤백됨
 * 재고는 주문할 때 차감하고, 입금 기한이 지난 결제 대기 주문은 자동 취소로 재고를 되돌림
 * 주문 상태를 바꾸는 모든 경로는 주문 행을 먼저 잠근 뒤 옵션 행을 잠금(주문 → 옵션 순서)
 */
@Service
@RequiredArgsConstructor
public class OrderService {
    /**
     * 재고 복원 이상 로거
     */
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * 주문번호 시각 부분 형식(UTC)
     */
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    /**
     * 주문번호 무작위 부분 문자 집합
     */
    private static final String ORDER_NO_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 주문번호 무작위 부분 생성기
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 운영 기록에 쓰는 입금 기한 표시 형식(한국 시간)
     */
    private static final DateTimeFormatter DUE_DISPLAY = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)
            .withZone(PaymentDeadlinePolicy.SEOUL);

    /**
     * 운영 기록의 주문 대상 종류
     */
    private static final String AUDIT_TARGET = "ORDERS";

    /**
     * 주문 저장소
     */
    private final OrderRepository orders;

    /**
     * 주문 상품 저장소
     */
    private final OrderItemRepository items;

    /**
     * 주문 상태 이력 저장소
     */
    private final OrderStatusHistoryRepository histories;

    /**
     * 옵션 저장소
     */
    private final ProductVariantRepository variants;

    /**
     * 상품 저장소
     */
    private final ProductRepository products;

    /**
     * 재고 이력 저장소
     */
    private final InventoryAdjustmentRepository adjustments;

    /**
     * 회원 저장소
     */
    private final UserRepository users;

    /**
     * 입금 기한 설정 조회와 운영 기록 저장
     */
    private final SystemService system;

    /**
     * 목록·대표 사진·기한 지난 주문 조회용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 고객 주문 생성
     *
     * @param userId 주문자 ID
     * @return 주문 상품·상태 이력을 포함한 결제 대기 주문
     * @throws ApiException 옵션 중복·판매하지 않는 옵션·재고 부족(400)
     */
    @Transactional
    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        Placed placed = place(userId, request.items(), request.paymentMethodOrDefault(), request.contact(),
                ChangeActorType.CUSTOMER, userId, "주문 생성");
        return OrderResponse.of(placed.order(), placed.items().stream().map(OrderItemResponse::from).toList(),
                List.of(OrderStatusHistoryResponse.from(placed.history())), null);
    }

    /**
     * 운영자 대리 주문 생성
     *
     * 일부 반품·교환처럼 원 주문을 전액 환불로 닫은 뒤 남길 상품만 다시 주문으로 받을 때 사용
     * 주문·재고·이력·입금 확인이 하나의 트랜잭션에서 처리되고 실패 시 함께 롤백됨
     *
     * 1. 원 주문과 그 주문자(탈퇴하지 않은 회원) 확인
     * 2. 원 주문의 연락처·배송지로 고객 주문과 같은 규칙의 주문 생성, 재고 차감
     * 3. 이미 받은 돈으로 처리하면 바로 입금 확인으로 전이
     * 4. 운영 기록 저장
     *
     * 새 주문 행은 커밋 전까지 이 트랜잭션에서만 보이므로 옵션을 먼저 잠가도 주문 → 옵션 잠금 순서와 충돌하지 않음
     *
     * @param actorUserId 주문을 만든 운영자 ID, 재고 이력의 작업자
     * @return 주문 상품·상태 이력·주문자를 포함한 주문
     * @throws ApiException 원 주문 없음(404), 주문자가 탈퇴했거나 없음(409), 옵션 중복·판매하지 않는 옵션·재고 부족(400)
     */
    @Transactional
    public OrderResponse createForCustomer(UUID actorUserId, CreateProxyOrderRequest request) {
        OrderEntity source = orders.findById(request.sourceOrderId()).orElseThrow(OrderService::orderNotFound);
        UserEntity customer = source.getUserId() == null ? null
                : users.findByIdAndDeletedAtIsNull(source.getUserId()).orElse(null);
        if (customer == null) throw ApiException.conflict("탈퇴한 회원 앞으로는 주문을 만들 수 없습니다.");

        // 원 주문의 결제 수단·연락처로 새 주문 생성
        Placed placed = place(customer.getId(), request.items(), source.getPaymentMethod(), source.contact(),
                ChangeActorType.ADMIN, actorUserId, "운영자 대리 주문 · 원 주문 " + source.getOrderNo());
        OrderEntity order = placed.order();
        if (request.paidRequested()) {
            transition(order, OrderStatus.PAID, ChangeActorType.ADMIN, "원 주문 결제 금액으로 처리", actorUserId, false);
        }
        system.log(actorUserId, AdminAuditAction.CREATE, AUDIT_TARGET, order.getId().toString(),
                "주문 %s 대리 생성 · 회원 %s · 원 주문 %s · %s".formatted(order.getOrderNo(), customer.getUsername(),
                        source.getOrderNo(), request.paidRequested() ? "입금 확인" : "결제 대기"));
        return detail(order);
    }

    /**
     * 결제 대기 주문 저장과 재고 차감
     *
     * 1. 옵션 중복 확인
     * 2. 옵션 행을 ID 순서로 잠금(동시 주문 직렬화, 교착 방지)
     * 3. 판매 여부·재고 확인 후 서버 가격으로 합계 계산
     * 4. 입금 기한을 계산해 주문·주문 상품 스냅샷 저장, 재고 차감과 재고 이력, 생성 이력 기록
     *
     * @param userId 주문자 ID
     * @param actor 생성 이력의 주체
     * @param actorUserId 재고 이력의 작업자(고객 본인 또는 운영자)
     * @param reason 생성 이력 사유
     * @return 저장한 주문·주문 상품·생성 이력
     * @throws ApiException 옵션 중복·판매하지 않는 옵션·재고 부족(400)
     */
    private Placed place(UUID userId, List<CreateOrderRequest.Item> lines, OrderPaymentMethod paymentMethod,
            OrderContact contact, ChangeActorType actor, UUID actorUserId, String reason) {
        List<UUID> variantIds = lines.stream().map(CreateOrderRequest.Item::variantId).toList();
        if (new HashSet<>(variantIds).size() != variantIds.size()) {
            throw ApiException.badRequest("동일 옵션은 한 번만 주문해주세요.");
        }

        // 잠금 후 조회한 최신 재고로 검증
        Map<UUID, ProductVariantEntity> variantById = variants.findAllForUpdate(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        Map<UUID, ProductEntity> productById = products.findAllById(variantById.values().stream()
                        .map(ProductVariantEntity::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        int total = 0;
        for (CreateOrderRequest.Item line : lines) {
            ProductVariantEntity variant = variantById.get(line.variantId());
            ProductEntity product = variant == null ? null : productById.get(variant.getProductId());
            if (variant == null || !variant.isActive() || product == null || !product.isVisible() || product.isDeleted()) {
                throw ApiException.badRequest("상품 옵션을 찾을 수 없습니다: " + line.variantId());
            }
            if (variant.getStock() < line.quantity()) {
                throw ApiException.badRequest("재고 부족: " + product.getName() + " " + variant.getName());
            }
            total += variant.getPriceAmount() * line.quantity();
        }

        // 주문·주문 상품 저장과 재고 차감
        OrderEntity order = orders.save(OrderEntity.place(newOrderNo(), userId, paymentMethod, total, contact,
                PaymentDeadlinePolicy.dueAt(Instant.now(), system.paymentDueDays())));
        Map<UUID, String> thumbnails = thumbnails(productById.keySet());
        List<OrderItemEntity> created = new ArrayList<>();
        for (CreateOrderRequest.Item line : lines) {
            ProductVariantEntity variant = variantById.get(line.variantId());
            ProductEntity product = productById.get(variant.getProductId());
            created.add(items.save(OrderItemEntity.snapshot(order.getId(), variant.getId(), product.getName(),
                    variant.getName(), thumbnails.get(product.getId()), variant.getPriceAmount(), line.quantity())));
            variant.adjustStock(-line.quantity());
            adjustments.save(InventoryAdjustmentEntity.of(variant.getId(), actorUserId, -line.quantity(),
                    "주문 " + order.getOrderNo()));
        }
        OrderStatusHistoryEntity history = histories.save(OrderStatusHistoryEntity.of(order.getId(), null,
                OrderStatus.PENDING_PAYMENT, actor, reason));
        return new Placed(order, created, history);
    }

    /**
     * 운영자 주문 상세 조회
     *
     * @return 주문 상품(당시 옵션 포함)·상태 이력·주문자
     * @throws ApiException 주문이 없을 때(404)
     */
    @Transactional(readOnly = true)
    public OrderResponse detail(UUID id) {
        return detail(orders.findById(id).orElseThrow(OrderService::orderNotFound));
    }

    /**
     * 고객 본인 주문 상세 조회
     *
     * @return 주문 상세
     * @throws ApiException 주문이 없을 때(404), 다른 사람의 주문일 때(403)
     */
    @Transactional(readOnly = true)
    public OrderResponse detailForCustomer(UUID id, UUID userId) {
        OrderEntity order = orders.findById(id).orElseThrow(OrderService::orderNotFound);
        if (!order.isOwnedBy(userId)) throw ApiException.forbidden("주문에 접근할 수 없습니다.");
        return detail(order);
    }

    /**
     * 고객 본인 주문 목록 조회
     *
     * @return 최근 주문 순, 주문 상품 포함
     */
    @Transactional(readOnly = true)
    public OrderPage listForCustomer(UUID userId, Paging paging) {
        return page(ORDERS.USERID.eq(userId), paging, false);
    }

    /**
     * 운영자 전체 주문 목록 조회
     *
     * @param status 주문 상태, {@code null}이면 전체
     * @param search 주문번호·주문자 이름·받는 사람·주문자 연락처·회원 아이디 부분 일치(대소문자 무시), 비어 있으면 전체
     * @return 최근 주문 순, 주문 상품·주문자 포함
     */
    @Transactional(readOnly = true)
    public OrderPage listAll(Paging paging, OrderStatus status, String search) {
        // 상태 조건
        Condition condition = status == null ? DSL.noCondition() : ORDERS.STATUS.eq(status.name());
        // 검색어 조건, 회원 아이디는 회원 테이블 하위 조회로 비교
        if (!search.isEmpty()) {
            condition = condition.and(ORDERS.ORDERNO.containsIgnoreCase(search)
                    .or(ORDERS.BUYERNAME.containsIgnoreCase(search))
                    .or(ORDERS.SHIPNAME.containsIgnoreCase(search))
                    .or(ORDERS.BUYERPHONE.containsIgnoreCase(search))
                    .or(ORDERS.USERID.in(DSL.select(USERS.ID).from(USERS).where(USERS.USERNAME.containsIgnoreCase(search)))));
        }
        return page(condition, paging, true);
    }

    /**
     * 운영자 상태 변경
     *
     * 주문 행을 잠근 뒤 {@link #transition}으로 처리
     *
     * @param actorUserId 변경한 운영자 ID, 재고 이력의 작업자
     * @param restock 전액 환불 시 주문 수량을 재고로 되돌릴지 여부
     * @return 주문 상품·상태 이력을 포함한 주문
     * @throws ApiException 주문 없음(404), 허용되지 않는 전이(409), 전액 환불이 아닌데 재입고 선택(400)
     */
    @Transactional
    public OrderResponse changeStatus(UUID id, OrderStatus to, String reason, UUID actorUserId, boolean restock) {
        if (restock && to != OrderStatus.FULLY_REFUNDED) {
            throw ApiException.badRequest("재고로 되돌리기는 전액 환불에서만 선택할 수 있습니다.");
        }
        return transition(orders.findForUpdate(id).orElseThrow(OrderService::orderNotFound), to, ChangeActorType.ADMIN,
                reason, actorUserId, restock);
    }

    /**
     * 운영자 입금 기한 변경
     *
     * 주문 행을 잠가 자동 취소·입금 확인과 직렬화한 뒤 변경, 운영 기록에 이전·새 기한을 남김
     *
     * @param dueAt 새 입금 기한
     * @param actorUserId 변경한 운영자 ID
     * @return 주문 상세
     * @throws ApiException 주문 없음(404), 결제 대기가 아님(409), 기한이 지금 이전이거나 최대 연장 범위를 넘을 때(400)
     */
    @Transactional
    public OrderResponse changePaymentDue(UUID id, Instant dueAt, UUID actorUserId) {
        OrderEntity order = orders.findForUpdate(id).orElseThrow(OrderService::orderNotFound);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("결제 대기 주문만 입금 기한을 바꿀 수 있습니다.");
        }
        Instant now = Instant.now();
        if (!dueAt.isAfter(now)) throw ApiException.badRequest("입금 기한은 지금 이후로 정해주세요.");
        if (dueAt.isAfter(now.plus(Duration.ofDays(PaymentDeadlinePolicy.MAX_DAYS)))) {
            throw ApiException.badRequest("입금 기한은 %d일 이내로 정해주세요.".formatted(PaymentDeadlinePolicy.MAX_DAYS));
        }
        // 기한 변경과 운영 기록
        String before = order.getPaymentDueAt() == null ? "없음" : DUE_DISPLAY.format(order.getPaymentDueAt());
        order.changePaymentDue(dueAt);
        system.log(actorUserId, AdminAuditAction.UPDATE, AUDIT_TARGET, order.getId().toString(),
                "주문 %s 입금 기한 변경 %s → %s".formatted(order.getOrderNo(), before, DUE_DISPLAY.format(dueAt)));
        return detail(order);
    }

    /**
     * 입금 기한이 지난 결제 대기 주문 ID 조회
     *
     * 잠금 없이 후보만 고름, 실제 취소 여부는 {@link #cancelIfPaymentOverdue}가 주문을 잠근 뒤 다시 판단
     *
     * @param limit 최대 건수
     * @return 기한이 오래 지난 순 주문 ID
     */
    @Transactional(readOnly = true)
    public List<UUID> paymentOverdueOrderIds(int limit) {
        return dsl.select(ORDERS.ID).from(ORDERS)
                .where(ORDERS.STATUS.eq(OrderStatus.PENDING_PAYMENT.name()), ORDERS.PAYMENTDUEAT.le(Instant.now()))
                .orderBy(ORDERS.PAYMENTDUEAT, ORDERS.ID)
                .limit(limit)
                .fetch(ORDERS.ID);
    }

    /**
     * 입금 기한이 지난 주문 자동 취소
     *
     * 주문 하나를 하나의 트랜잭션에서 처리, 실패 시 그 주문의 상태·재고·이력이 함께 롤백됨
     *
     * 1. 주문 행 잠금(운영자의 입금 확인·기한 연장, 고객 취소와 직렬화)
     * 2. 잠근 뒤에도 결제 대기 중이고 기한이 지났는지 다시 확인, 아니면 아무것도 바꾸지 않음
     * 3. 취소 전이로 재고 복원과 재고 이력, 시스템 주체의 상태 이력 기록
     *
     * @return 취소했으면 {@code true}, 그사이 입금 확인·연장·취소됐거나 주문이 없으면 {@code false}
     */
    @Transactional
    public boolean cancelIfPaymentOverdue(UUID id) {
        OrderEntity order = orders.findForUpdate(id).orElse(null);
        if (order == null || !order.isPaymentOverdue(Instant.now())) return false;
        transition(order, OrderStatus.CANCELED, ChangeActorType.SYSTEM, "입금 기한 경과 자동 취소", null, false);
        return true;
    }

    /**
     * 고객 주문 취소
     *
     * 결제 대기 중인 본인 주문만 취소, 재고 복원
     *
     * @return 취소된 주문
     * @throws ApiException 주문 없음(404), 다른 사람의 주문(403), 결제 대기가 아님(409)
     */
    @Transactional
    public OrderResponse cancelForCustomer(UUID id, UUID userId) {
        OrderEntity order = orders.findForUpdate(id).orElseThrow(OrderService::orderNotFound);
        if (!order.isOwnedBy(userId)) throw ApiException.forbidden("주문에 접근할 수 없습니다.");
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("결제 대기 주문만 직접 취소할 수 있습니다.");
        }
        return transition(order, OrderStatus.CANCELED, ChangeActorType.CUSTOMER, "고객 주문 취소", userId, false);
    }

    /**
     * 잠긴 주문의 상태 전이
     *
     * 1. 허용 전이 확인
     * 2. 취소, 또는 재입고를 선택한 전액 환불이면 재고 복원과 재고 이력
     * 3. 주문·주문 상품 상태와 시각 반영, 상태 이력 기록
     *
     * 취소와 전액 환불은 더 전이할 수 없는 종료 상태라 한 주문의 재고는 한 번만 복원됨
     *
     * @param actorUserId 재고 이력의 작업자(고객·운영자), 시스템 자동 취소면 {@code null}
     * @param restock 전액 환불 시 재고 복원 여부, 다른 전이에서는 무시
     * @return 주문 상품·전체 상태 이력을 포함한 주문
     * @throws ApiException 허용되지 않는 전이(409)
     */
    private OrderResponse transition(OrderEntity order, OrderStatus to, ChangeActorType actor, String reason,
            UUID actorUserId, boolean restock) {
        if (!OrderTransitions.allowed(order.getStatus(), to)) {
            throw ApiException.conflict("현재 주문 상태에서 변경할 수 없습니다.");
        }
        // 상태별 주문 변경, 취소는 재고 복원 포함
        OrderStatus from = order.getStatus();
        List<OrderItemEntity> orderItems = items.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId());
        Instant now = Instant.now();
        switch (to) {
            case CANCELED -> {
                restoreStock(orderItems, actorUserId, (actor == ChangeActorType.SYSTEM ? "입금 기한 경과 자동 취소 " : "주문 취소 ")
                        + order.getOrderNo());
                order.cancel(now);
            }
            case PAID -> order.markPaid(now);
            case FULLY_REFUNDED -> {
                if (restock) restoreStock(orderItems, actorUserId, "환불 재입고 " + order.getOrderNo());
                order.refundFully(now);
            }
            default -> order.moveTo(to);
        }
        OrderTransitions.itemStatus(to).ifPresent(status -> orderItems.forEach(item -> item.changeStatus(status)));

        // 이력 저장 후 전체 이력을 다시 읽어 응답
        histories.save(OrderStatusHistoryEntity.of(order.getId(), from, to, actor, reason));
        histories.flush();
        return OrderResponse.of(order, orderItems.stream().map(OrderItemResponse::from).toList(),
                histories.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                        .map(OrderStatusHistoryResponse::from).toList(), null);
    }

    /**
     * 주문 수량의 옵션 재고 복원과 재고 이력 기록
     *
     * 주문 생성과 같은 ID 순서로 옵션을 잠가 교착 방지
     * 옵션을 알 수 없는 기존 주문 상품({@code variantId} 없음)은 되돌릴 재고가 없으므로 건너뛰고 경고만 남김
     *
     * @param actorUserId 재고 이력 작업자, 시스템 자동 취소면 {@code null}
     * @param reason 재고 이력 사유
     */
    private void restoreStock(List<OrderItemEntity> orderItems, UUID actorUserId, String reason) {
        Map<UUID, ProductVariantEntity> locked = variants.findAllForUpdate(
                        orderItems.stream().map(OrderItemEntity::getVariantId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        for (OrderItemEntity item : orderItems) {
            ProductVariantEntity variant = locked.get(item.getVariantId());
            if (variant == null) {
                log.warn("주문 상품 {}의 옵션을 찾을 수 없어 재고 {}개를 되돌리지 못했습니다 ({})", item.getId(),
                        item.getQuantity(), reason);
                continue;
            }
            variant.adjustStock(item.getQuantity());
            adjustments.save(InventoryAdjustmentEntity.of(item.getVariantId(), actorUserId, item.getQuantity(), reason));
        }
    }

    /**
     * 주문 상세 응답 조립
     *
     * @return 주문 상품(당시 옵션)·상태 이력·주문자(삭제 회원이면 {@code null})
     */
    private OrderResponse detail(OrderEntity order) {
        List<OrderItemEntity> orderItems = items.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId());
        Map<UUID, ProductVariantEntity> variantById = variants.findAllById(
                        orderItems.stream().map(OrderItemEntity::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        UserResponse user = order.getUserId() == null ? null
                : users.findByIdAndDeletedAtIsNull(order.getUserId()).map(UserResponse::from).orElse(null);
        return OrderResponse.of(order,
                orderItems.stream().map(item -> OrderItemResponse.from(item,
                        variantById.containsKey(item.getVariantId())
                                ? ProductVariantResponse.from(variantById.get(item.getVariantId())) : null)).toList(),
                histories.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                        .map(OrderStatusHistoryResponse::from).toList(),
                user);
    }

    /**
     * 주문 목록 쪽 조립
     *
     * 쪽의 주문 ID를 jOOQ로 고른 뒤 주문·주문 상품·주문자를 각각 한 번씩 조회
     *
     * @param withUser {@code true}면 주문자 포함
     * @return 최근 주문 순 쪽
     */
    private OrderPage page(Condition condition, Paging paging, boolean withUser) {
        List<UUID> ids = dsl.select(ORDERS.ID).from(ORDERS).where(condition)
                .orderBy(ORDERS.CREATEDAT.desc(), ORDERS.ID.desc())
                .limit(paging.size()).offset(paging.offset())
                .fetch(ORDERS.ID);
        Map<UUID, OrderEntity> orderById = orders.findAllById(ids).stream()
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        Map<UUID, List<OrderItemResponse>> itemsByOrder = items.findByOrderIdIn(ids).stream()
                .sorted(Comparator.comparing(OrderItemEntity::getCreatedAt).thenComparing(OrderItemEntity::getId))
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId,
                        Collectors.mapping(OrderItemResponse::from, Collectors.toList())));
        Map<UUID, UserResponse> userById = withUser
                ? users.findAllById(orderById.values().stream().map(OrderEntity::getUserId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                        .filter(user -> !user.isDeleted())
                        .collect(Collectors.toMap(UserEntity::getId, UserResponse::from))
                : Map.of();
        List<OrderResponse> page = ids.stream().map(orderById::get).map(order -> OrderResponse.of(order,
                itemsByOrder.getOrDefault(order.getId(), List.of()), null,
                withUser ? userById.get(order.getUserId()) : null)).toList();
        return new OrderPage(page, dsl.fetchCount(ORDERS, condition), paging.page(), paging.size());
    }

    /**
     * 상품별 대표 사진 조회
     *
     * @return 상품 ID별 표시 순서가 가장 앞선 사진 키, 사진 없는 상품은 키 없음
     */
    private Map<UUID, String> thumbnails(Collection<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        return dsl.select(PRODUCT_IMAGES.PRODUCTID, PRODUCT_IMAGES.OBJECTKEY)
                .distinctOn(PRODUCT_IMAGES.PRODUCTID)
                .from(PRODUCT_IMAGES)
                .where(PRODUCT_IMAGES.PRODUCTID.in(productIds))
                .orderBy(PRODUCT_IMAGES.PRODUCTID, PRODUCT_IMAGES.SORTORDER)
                .fetchMap(PRODUCT_IMAGES.PRODUCTID, PRODUCT_IMAGES.OBJECTKEY);
    }

    /**
     * 주문번호 생성
     *
     * @return {@code ORD-<UTC yyyyMMddHHmmss>-<무작위 6자>}
     */
    private static String newOrderNo() {
        StringBuilder random = new StringBuilder();
        for (int i = 0; i < 6; i++) random.append(ORDER_NO_ALPHABET.charAt(RANDOM.nextInt(ORDER_NO_ALPHABET.length())));
        return "ORD-" + ORDER_NO_TIME.format(Instant.now()) + "-" + random;
    }

    /**
     * 주문 없음 예외 생성
     *
     * @return 404 예외 객체
     */
    private static ApiException orderNotFound() {
        return ApiException.notFound("주문을 찾을 수 없습니다.");
    }

    /**
     * 방금 저장한 주문
     *
     * @param order 결제 대기 주문
     * @param items 주문 상품, 요청 순서
     * @param history 생성 이력
     */
    private record Placed(OrderEntity order, List<OrderItemEntity> items, OrderStatusHistoryEntity history) {}
}
