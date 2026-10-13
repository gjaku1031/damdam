package io.github.gjaku1031.damdam.order.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.order.domain.OrderEntity;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;

/**
 * 주문 저장소
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    /**
     * 주문 행 잠금 조회
     *
     * 상태 변경·결제 처리를 직렬화하기 위해 사용
     *
     * @return 잠근 주문, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findForUpdate(UUID id);

    /**
     * 회원 본인 주문 행 잠금 조회
     *
     * @return 잠근 주문, 다른 회원의 주문이거나 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id and o.userId = :userId")
    Optional<OrderEntity> findForUpdate(UUID id, UUID userId);

    /**
     * 회원 본인 주문 조회
     *
     * @return 주문, 다른 회원의 주문이거나 없으면 빈 값
     */
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * 주문번호로 주문 조회
     *
     * @return 주문, 없으면 빈 값
     */
    Optional<OrderEntity> findByOrderNo(String orderNo);

    /**
     * 회원의 해당 상태 주문 수
     *
     * @return 주문 수
     */
    long countByUserIdAndStatus(UUID userId, OrderStatus status);
}
