package io.github.gjaku1031.damdam.inventory.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.inventory.domain.InventoryReservationEntity;

/**
 * 재고 예약 저장소
 */
public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {}
