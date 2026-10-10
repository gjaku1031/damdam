package io.github.gjaku1031.damdam.storage.dto;

/**
 * 제공할 사진 한 장
 *
 * @param data 사진 바이트
 * @param contentType MIME 형식, 변환본은 {@code image/webp}, 변환본이 없어 원본을 보낼 때는 원본 형식
 * @param cacheable 공개 사용처에 쓰여 누구에게나 같은 응답이면 참, 브라우저·CDN 캐시 허용 여부
 */
public record StoredImage(byte[] data, String contentType, boolean cacheable) {}
