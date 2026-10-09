-- 운영자가 관리하는 상품 분류, 상품은 기존처럼 분류명(products.category)을 저장
CREATE TABLE "product_categories" (
  "id" uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  "name" varchar(50) NOT NULL UNIQUE
);

-- 앞뒤 공백을 정리하고 기존 상품의 분류명을 그대로 분류로 등록
UPDATE "products" SET "category" = NULLIF(btrim("category"), '') WHERE "category" IS DISTINCT FROM NULLIF(btrim("category"), '');
INSERT INTO "product_categories" ("name")
SELECT DISTINCT "category" FROM "products" WHERE "category" IS NOT NULL;
