package io.github.gjaku1031.damdam.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 분류 등록·이름 변경 요청
 *
 * @param name 분류명, 최대 50자, 앞뒤 공백 제거 후 비어 있으면 400
 */
public record ProductCategoryRequest(@NotNull @Size(max = 50, message = "분류명은 50자 이하로 입력해주세요.") String name) {}
