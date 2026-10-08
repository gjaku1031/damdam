-- 주문 배송 메모: 고객이 남긴 배송 요청, 없으면 빈 문자열
ALTER TABLE "orders" ADD COLUMN "shipMemo" varchar(200) NOT NULL DEFAULT '';
