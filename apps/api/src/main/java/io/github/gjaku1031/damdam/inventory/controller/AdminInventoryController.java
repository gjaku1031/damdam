package io.github.gjaku1031.damdam.inventory.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.inventory.dto.AdjustInventoryRequest;
import io.github.gjaku1031.damdam.inventory.dto.AdjustmentResult;
import io.github.gjaku1031.damdam.inventory.dto.InventoryAdjustmentResponse;
import io.github.gjaku1031.damdam.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 재고 관리 API
 */
@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public final class AdminInventoryController {
    /**
     * 재고 서비스
     */
    private final InventoryService inventory;

    /**
     * 옵션의 재고 조정 이력 조회
     *
     * @param page 쪽 번호, 생략 시 1
     * @param size 쪽 크기, 생략 시 20
     * @return 최근 조정 순 기록 쪽
     */
    @GetMapping("/variants/{variantId}/history")
    public ItemPage<InventoryAdjustmentResponse> history(@PathVariable UUID variantId,
            @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return inventory.history(variantId, Paging.of(page, size, 20));
    }

    /**
     * 옵션 재고 조정
     *
     * @return 저장한 기록과 조정 후 재고
     */
    @PostMapping("/variants/{variantId}/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResult adjust(@PathVariable UUID variantId, @Valid @RequestBody AdjustInventoryRequest body,
            CurrentUser user) {
        return inventory.adjust(variantId, body.delta(), body.reason(), user.id());
    }
}
