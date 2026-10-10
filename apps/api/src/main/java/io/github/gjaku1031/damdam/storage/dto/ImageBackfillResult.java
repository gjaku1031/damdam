package io.github.gjaku1031.damdam.storage.dto;

/**
 * 변환본 일괄 생성 결과
 *
 * @param converted 변환본을 새로 만든 사진 수
 * @param skipped 변환본이 이미 모두 있어 건너뛴 사진 수
 * @param failed 원본이 없거나 해석하지 못해 실패한 사진 수
 */
public record ImageBackfillResult(int converted, int skipped, int failed) {}
