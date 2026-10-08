package io.github.gjaku1031.damdam.product.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 부분 수정 요청
 *
 * 보낸 항목만 바꾸고 {@code null}·생략은 바꾸지 않음
 *
 * @param name 상품명(최대 200자)
 * @param description 설명(최대 50000자)
 * @param category 분류(최대 50자)
 * @param visible 공개 여부
 * @param variants 옵션 전체 1~30개
 * @param images 사진 전체 최대 20개(표시 순서)
 */
public record UpdateProductRequest(
        @Size(max = 200) String name,
        @Size(max = 50000) String description,
        @Size(max = 50) String category,
        Boolean visible,
        @Size(min = 1, max = 30) List<@NotNull @Valid VariantInput> variants,
        @Size(max = 20) List<@NotNull @Valid ImageInput> images) implements ProductChanges {
    /**
     * 공개 여부만 바꾸는 수정 생성
     *
     * @return 공개 여부 외 모든 항목이 {@code null}인 수정
     */
    public static UpdateProductRequest visibility(boolean visible) {
        return new UpdateProductRequest(null, null, null, visible, null, null);
    }
}
