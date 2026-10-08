package io.github.gjaku1031.damdam.cart.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.cart.domain.CartEntity;
import io.github.gjaku1031.damdam.cart.domain.CartItemEntity;
import io.github.gjaku1031.damdam.cart.dto.CartItemResponse;
import io.github.gjaku1031.damdam.cart.repository.CartItemRepository;
import io.github.gjaku1031.damdam.cart.repository.CartRepository;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.product.domain.ProductEntity;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import io.github.gjaku1031.damdam.product.repository.ProductRepository;
import io.github.gjaku1031.damdam.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;

/**
 * 서버 장바구니
 *
 * 사용자당 장바구니 하나를 처음 접근할 때 생성하고, 담을 때의 옵션 가격을 스냅샷으로 남김
 */
@Service
@RequiredArgsConstructor
public class CartService {
    /**
     * 장바구니 저장소
     */
    private final CartRepository carts;

    /**
     * 장바구니 항목 저장소
     */
    private final CartItemRepository items;

    /**
     * 상품 옵션 저장소
     */
    private final ProductVariantRepository variants;

    /**
     * 상품 저장소
     */
    private final ProductRepository products;

    /**
     * 장바구니 항목 목록 조회
     *
     * 장바구니가 없으면 생성하므로 쓰기 트랜잭션에서 실행
     *
     * @return 담은 순서의 항목, 옵션·상품이 없어진 항목은 해당 값이 {@code null}
     */
    @Transactional
    public List<CartItemResponse> items(UUID userId) {
        // 항목 조회 후 옵션·상품을 일괄 조회해 ID별로 묶음
        List<CartItemEntity> cartItems = items.findByCartIdOrderByCreatedAtAscIdAsc(cartOf(userId).getId());
        Map<UUID, ProductVariantEntity> variantById = variants.findAllById(
                cartItems.stream().map(CartItemEntity::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        Map<UUID, ProductEntity> productById = products.findAllById(
                variantById.values().stream().map(ProductVariantEntity::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        // 항목 순서대로 옵션·상품을 붙여 응답 조립
        return cartItems.stream().map(item -> {
            ProductVariantEntity variant = variantById.get(item.getVariantId());
            return CartItemResponse.of(item, variant, variant == null ? null : productById.get(variant.getProductId()));
        }).toList();
    }

    /**
     * 옵션 담기
     *
     * 같은 옵션이 이미 담겨 있으면 수량을 더하고 기존 가격 스냅샷 유지
     *
     * @param quantity 더할 수량
     * @return 담은 뒤의 항목
     * @throws ApiException 옵션이 없을 때(404)
     */
    @Transactional
    public CartItemResponse add(UUID userId, UUID variantId, int quantity) {
        CartEntity cart = cartOf(userId);
        ProductVariantEntity variant = variants.findById(variantId)
                .orElseThrow(() -> ApiException.notFound("상품 옵션을 찾을 수 없습니다."));
        // 기존 항목이면 수량 합산, 없으면 현재 가격으로 새 항목 저장
        CartItemEntity item = items.findByCartIdAndVariantId(cart.getId(), variantId)
                .map(existing -> {
                    existing.updateQuantity(existing.getQuantity() + quantity);
                    return existing;
                })
                .orElseGet(() -> items.save(CartItemEntity.of(cart.getId(), variantId, quantity, variant.getPriceAmount())));
        // 응답의 수정 시각을 반영하려고 flush 후 조립
        items.flush();
        return response(item, variant);
    }

    /**
     * 항목 수량 변경
     *
     * @param quantity 새 수량
     * @return 변경한 항목
     * @throws ApiException 본인 장바구니 항목이 아닐 때(404)
     */
    @Transactional
    public CartItemResponse updateQuantity(UUID itemId, int quantity, UUID userId) {
        CartItemEntity item = owned(itemId, userId);
        item.updateQuantity(quantity);
        // 응답의 수정 시각을 반영하려고 flush 후 조립
        items.flush();
        return response(item, variants.findById(item.getVariantId()).orElse(null));
    }

    /**
     * 항목 삭제
     *
     * @throws ApiException 본인 장바구니 항목이 아닐 때(404)
     */
    @Transactional
    public void remove(UUID itemId, UUID userId) {
        items.delete(owned(itemId, userId));
    }

    /**
     * 본인 장바구니 항목 조회
     *
     * @return 항목
     * @throws ApiException 없거나 다른 고객의 항목일 때(404)
     */
    private CartItemEntity owned(UUID itemId, UUID userId) {
        return items.findOwned(itemId, userId).orElseThrow(() -> ApiException.notFound("장바구니 항목을 찾을 수 없습니다."));
    }

    /**
     * 사용자 장바구니 조회, 없으면 생성
     *
     * @return 장바구니
     */
    private CartEntity cartOf(UUID userId) {
        return carts.findByUserId(userId).orElseGet(() -> carts.saveAndFlush(CartEntity.of(userId)));
    }

    /**
     * 항목 응답 조립
     *
     * @param variant 옵션, 없으면 {@code null}
     * @return 옵션의 상품까지 담은 응답
     */
    private CartItemResponse response(CartItemEntity item, ProductVariantEntity variant) {
        ProductEntity product = variant == null ? null : products.findById(variant.getProductId()).orElse(null);
        return CartItemResponse.of(item, variant, product);
    }
}
