package io.github.gjaku1031.damdam.storage.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 업로드할 때 미리 만들어 두는 크기별 변환본
 *
 * 가로 기준으로 줄인 WebP, 원본이 더 작으면 키우지 않고 원본 크기로 만듦
 */
public enum ImageVariant {
    /**
     * 소: 상품 카드·주문 내역·목록 썸네일
     */
    SMALL("s", 400),

    /**
     * 중: 고해상도 화면의 카드, 휴대폰의 상세 사진
     */
    MEDIUM("m", 800),

    /**
     * 대: PC 상세 사진과 글 본문 사진, 크기를 지정하지 않은 요청의 기본값
     */
    LARGE("l", 1600);

    /**
     * 주소의 {@code size} 값이자 변환본 파일명 접미사
     */
    private final String code;

    /**
     * 변환본의 최대 가로(px)
     */
    private final int width;

    /**
     * 주소 표기와 최대 가로를 가진 변환본 종류 생성
     */
    ImageVariant(String code, int width) {
        this.code = code;
        this.width = width;
    }

    /**
     * 주소의 {@code size} 값이자 변환본 파일명 접미사
     *
     * @return {@code s}·{@code m}·{@code l}
     */
    public String code() {
        return code;
    }

    /**
     * 변환본의 최대 가로
     *
     * @return 가로(px)
     */
    public int width() {
        return width;
    }

    /**
     * 주소의 {@code size} 값에 해당하는 변환본 종류
     *
     * @param code {@code s}·{@code m}·{@code l}, 대소문자 구분
     * @return 변환본 종류, 정해 둔 값이 아니면 빈 값
     */
    public static Optional<ImageVariant> ofCode(String code) {
        return Arrays.stream(values()).filter(variant -> variant.code.equals(code)).findFirst();
    }
}
