package io.github.gjaku1031.damdam.common.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 오류 응답의 공통 본문
 *
 * 웹은 {@code message}와 {@code fields}를 읽음
 *
 * @param success 항상 {@code false}
 * @param statusCode HTTP 상태 코드
 * @param message 사용자에게 보여줄 메시지
 * @param fields 입력 경로별 오류 메시지, 없으면 {@code null}이며 JSON에서 생략
 * @param timestamp 응답 생성 시각
 */
public record ErrorResponse(
        boolean success,
        int statusCode,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fields,
        Instant timestamp) {
    /**
     * 현재 시각으로 오류 응답 생성
     *
     * @param status HTTP 상태 코드
     * @param message 사용자 메시지
     * @param fields 입력 경로별 오류 메시지, 없으면 {@code null}
     * @return 오류 응답 본문
     */
    public static ErrorResponse of(int status, String message, Map<String, String> fields) {
        return new ErrorResponse(false, status, message, fields, Instant.now());
    }
}
