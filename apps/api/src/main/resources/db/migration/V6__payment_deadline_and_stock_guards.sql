-- 입금 기한과 재고 보호
-- 1) 결제 대기 주문에 입금 기한을 두고, 2) 음수 재고를 DB에서 막고,
-- 3) 재고 이력 합계를 현재 재고와 맞추고, 4) 쓰지 않는 재고 예약 테이블을 지운다.
-- 조건을 어기는 데이터가 있으면 아무것도 바꾸지 않고 중단한다.
-- [jooq ignore start]
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM "product_variants" WHERE "stock" < 0) THEN
        RAISE EXCEPTION 'Negative stock exists; correct product_variants.stock before migrating.';
    END IF;
    IF EXISTS (SELECT 1 FROM "inventory_reservations" WHERE "status" = 'ACTIVE') THEN
        RAISE EXCEPTION 'Active inventory reservations exist; resolve them before migrating.';
    END IF;
END $$;
-- [jooq ignore stop]

-- 입금 기한: 주문할 때 계산해 저장, 기한 도입 전 주문은 NULL
ALTER TABLE "orders" ADD COLUMN "paymentDueAt" TIMESTAMP WITH TIME ZONE;

-- [jooq ignore start]
-- 이미 결제 대기 중인 주문은 적용일 + 2일 23:59:59(한국 시간)까지 기다린다. 적용 직후 한꺼번에 취소되지 않게 함
UPDATE "orders"
SET "paymentDueAt" = (date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') + interval '3 days' - interval '1 second')
        AT TIME ZONE 'Asia/Seoul'
WHERE "status" = 'PENDING_PAYMENT';

-- 자동 취소 대상 조회용, 결제 대기 주문만 담는 부분 인덱스
CREATE INDEX "idx_orders_pending_payment_due" ON "orders" ("paymentDueAt") WHERE "status" = 'PENDING_PAYMENT';

-- 재고 이력 보정: 옵션마다 현재 재고와 이력 합계의 차이를 한 건으로 기록
INSERT INTO "inventory_adjustments" ("variant_id", "delta", "reason")
SELECT v."id", v."stock" - COALESCE(s."total", 0), '이력 보정 (초기 재고 포함)'
FROM "product_variants" v
LEFT JOIN (SELECT "variant_id", SUM("delta") AS "total" FROM "inventory_adjustments" GROUP BY "variant_id") s
        ON s."variant_id" = v."id"
WHERE v."stock" <> COALESCE(s."total", 0);
-- [jooq ignore stop]

-- 음수 재고 금지
ALTER TABLE "product_variants" ADD CONSTRAINT "chk_product_variants_stock_nonnegative" CHECK ("stock" >= 0);

-- 재고 예약: 주문 시 재고를 바로 차감하고 입금 기한으로 회수하므로 사용하지 않음
DROP TABLE "inventory_reservations";
