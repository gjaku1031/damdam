package io.github.gjaku1031.damdam.cart.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.cart.dto.AddCartItemRequest;
import io.github.gjaku1031.damdam.cart.dto.CartItemResponse;
import io.github.gjaku1031.damdam.cart.dto.UpdateCartItemRequest;
import io.github.gjaku1031.damdam.cart.service.CartService;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.auth.LoginRequired;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 고객의 장바구니 API
 */
@RestController
@LoginRequired
@RequestMapping("/cart")
@RequiredArgsConstructor
public final class CartController {
    /**
     * 장바구니 서비스
     */
    private final CartService cart;

    /**
     * 장바구니 항목 목록 조회
     *
     * @return 담은 순서의 항목
     */
    @GetMapping("/items")
    public List<CartItemResponse> items(CurrentUser user) {
        return cart.items(user.id());
    }

    /**
     * 옵션 담기
     *
     * @return 담은 뒤의 항목
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse add(@Valid @RequestBody AddCartItemRequest body, CurrentUser user) {
        return cart.add(user.id(), body.variantId(), body.quantity());
    }

    /**
     * 항목 수량 변경
     *
     * @return 변경한 항목
     */
    @PatchMapping("/items/{id}")
    public CartItemResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCartItemRequest body,
            CurrentUser user) {
        return cart.updateQuantity(id, body.quantity(), user.id());
    }

    /**
     * 항목 삭제
     *
     * @return {@code {success: true}}
     */
    @DeleteMapping("/items/{id}")
    public Map<String, Boolean> remove(@PathVariable UUID id, CurrentUser user) {
        cart.remove(id, user.id());
        return Map.of("success", true);
    }
}
