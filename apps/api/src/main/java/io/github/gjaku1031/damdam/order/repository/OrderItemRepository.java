package io.github.gjaku1031.damdam.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.order.domain.OrderItemEntity;

/**
 * 주문 상품 저장소
 */
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {
    /**
     * 주문의 상품 목록 조회
     *
     * @return 생성 시각·ID 오름차순
     */
    List<OrderItemEntity> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);

    /**
     * 여러 주문의 상품 일괄 조회
     *
     * @param orderIds 주문 ID 목록
     * @return 순서 보장 없음, 주문 ID로 묶어서 사용
     */
    List<OrderItemEntity> findByOrderIdIn(Collection<UUID> orderIds);
}
