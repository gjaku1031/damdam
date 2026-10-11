-- 부분 환불 상태 제거: 환불은 전액 환불만 기록
-- 부분 환불은 금액·재고를 다루지 않고 상태 이름만 바꾸던 값이라, 해당 주문을 그 직전 상태로 되돌린다.
-- [jooq ignore start]
-- 부분 환불을 거쳐 전액 환불된 주문: 전액 환불 이력이 부분 환불 직전 상태에서 이어지게 함
UPDATE "order_status_histories" AS next
SET "fromStatus" = prev."fromStatus"
FROM "order_status_histories" AS prev
WHERE prev."orderId" = next."orderId"
  AND prev."toStatus" = 'PARTIALLY_REFUNDED'
  AND next."fromStatus" = 'PARTIALLY_REFUNDED';

-- 부분 환불 상태로 남은 주문: 직전 상태로 되돌리고 환불 금액·시각을 비움
UPDATE "orders" AS o
SET "status" = COALESCE(h."fromStatus", 'PAID'), "refundedAmount" = 0, "refundedAt" = NULL
FROM "order_status_histories" AS h
WHERE h."orderId" = o."id"
  AND h."toStatus" = 'PARTIALLY_REFUNDED'
  AND o."status" = 'PARTIALLY_REFUNDED';

-- 이력 없이 부분 환불 상태인 주문은 결제 완료로 둠
UPDATE "orders" SET "status" = 'PAID', "refundedAmount" = 0, "refundedAt" = NULL WHERE "status" = 'PARTIALLY_REFUNDED';

-- 부분 환불 이력 삭제, 이어 붙일 직전 이력이 없던 행은 결제 완료에서 이어진 것으로 둠
DELETE FROM "order_status_histories" WHERE "toStatus" = 'PARTIALLY_REFUNDED';
UPDATE "order_status_histories" SET "fromStatus" = 'PAID' WHERE "fromStatus" = 'PARTIALLY_REFUNDED';
-- [jooq ignore stop]
