-- 네이버페이 제거: 결제 수단은 무통장 입금만 사용
-- 네이버페이 결제·주문 기록이 하나라도 있으면 데이터를 지우지 않고 중단한다.
-- [jooq ignore start]
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM "payments")
            OR EXISTS (SELECT 1 FROM "orders" WHERE "paymentMethod" <> 'BANK_TRANSFER') THEN
        RAISE EXCEPTION 'NaverPay payment records exist; back them up and remove them before migrating.';
    END IF;
END $$;
-- [jooq ignore stop]

DROP TABLE "payment_refunds";
DROP TABLE "payment_status_histories";
DROP TABLE "payments";
DROP TABLE "payment_method_policies";
