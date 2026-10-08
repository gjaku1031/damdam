package io.github.gjaku1031.damdam.product.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 옵션(가격·재고 단위)
 *
 * 과거 주문이 참조하므로 삭제하지 않고 판매 중지로 남김
 */
@Getter
@Entity
@Table(name = "product_variants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariantEntity extends BaseEntity {
    /**
     * 상품 ID
     */
    @Column(nullable = false)
    private UUID productId;

    /**
     * 옵션명, 상품 안에서 유일
     */
    @Column(nullable = false)
    private String name;

    /**
     * 판매가(원)
     */
    @Column(nullable = false)
    private int priceAmount;

    /**
     * 통화 코드
     */
    @Column(nullable = false, length = 3)
    private String priceCurrency = "KRW";

    /**
     * 재고 수량
     */
    @Column(nullable = false)
    private int stock;

    /**
     * 판매 중 여부
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * 기본 옵션 여부, 상품마다 판매 중인 옵션 하나만 기본
     */
    @Column(nullable = false)
    private boolean isDefault;

    /**
     * 새 옵션 생성
     *
     * @param stock 초기 재고, 이후 변경은 재고 조정으로만 수행
     * @return 저장 전 옵션
     */
    public static ProductVariantEntity create(UUID productId, int stock) {
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.productId = productId;
        variant.stock = stock;
        return variant;
    }

    /**
     * 옵션명·가격·판매 여부·기본 여부 변경
     *
     * 재고는 바꾸지 않음
     */
    public void update(String name, int priceAmount, boolean active, boolean isDefault) {
        this.name = name;
        this.priceAmount = priceAmount;
        this.active = active;
        this.isDefault = isDefault;
    }

    /**
     * 판매 중지, 기본 옵션 지정 해제
     */
    public void retire() {
        active = false;
        isDefault = false;
    }

    /**
     * 재고 증감
     *
     * @param delta 양수면 입고, 음수면 출고
     * @throws IllegalStateException 결과 재고가 음수일 때
     */
    public void adjustStock(int delta) {
        if (stock + delta < 0) throw new IllegalStateException("재고가 부족합니다.");
        stock += delta;
    }
}
