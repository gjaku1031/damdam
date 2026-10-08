package io.github.gjaku1031.damdam.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.order.domain.OrderStatusHistoryEntity;

/**
 * 주문 상태 이력 저장소
 */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {
    /**
     * 주문의 상태 변경 이력 조회
     *
     * @return 생성 시각·ID 오름차순
     */
    List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
}
