package io.github.gjaku1031.damdam.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 사진 입력
 *
 * @param objectKey 검증을 마친 업로드 사진 키(최대 250자)
 */
public record ImageInput(@NotNull @Size(max = 250) String objectKey) {}
