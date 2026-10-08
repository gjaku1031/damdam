package io.github.gjaku1031.damdam.product.dto;

import java.util.List;

/**
 * 상품 등록·수정 입력
 *
 * 값이 {@code null}인 항목은 바꾸지 않음
 */
public interface ProductChanges {
    /**
     * 상품명
     *
     * @return 상품명, 바꾸지 않으면 {@code null}
     */
    String name();

    /**
     * 설명
     *
     * @return 설명, 바꾸지 않으면 {@code null}
     */
    String description();

    /**
     * 분류
     *
     * @return 분류, 바꾸지 않으면 {@code null}
     */
    String category();

    /**
     * 공개 여부
     *
     * @return 공개 여부, 바꾸지 않으면 {@code null}
     */
    Boolean visible();

    /**
     * 옵션 목록
     *
     * @return 옵션 전체, 바꾸지 않으면 {@code null}
     */
    List<VariantInput> variants();

    /**
     * 사진 목록
     *
     * @return 표시 순서의 사진 전체, 바꾸지 않으면 {@code null}
     */
    List<ImageInput> images();
}
