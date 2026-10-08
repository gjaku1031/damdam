package io.github.gjaku1031.damdam.product.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 등록 요청
 *
 * @param name 상품명, 필수(최대 200자)
 * @param description 설명(최대 50000자), 생략 가능
 * @param category 분류(최대 50자), 생략 가능
 * @param visible 공개 여부, 생략 가능
 * @param variants 옵션 1~30개, 필수
 * @param images 사진 최대 20개(표시 순서), 필수
 */
public record CreateProductRequest(
        @NotNull @Size(max = 200) String name,
        @Size(max = 50000) String description,
        @Size(max = 50) String category,
        Boolean visible,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid VariantInput> variants,
        @NotNull @Size(max = 20) List<@NotNull @Valid ImageInput> images) implements ProductChanges {}
