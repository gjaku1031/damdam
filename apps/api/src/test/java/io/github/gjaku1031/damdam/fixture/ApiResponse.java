package io.github.gjaku1031.damdam.fixture;

import java.net.http.HttpHeaders;

import tools.jackson.databind.JsonNode;

/**
 * 테스트 요청의 응답
 *
 * @param status HTTP 상태 코드
 * @param body JSON 본문, 본문이 없거나 JSON이 아니면 빈 객체
 * @param headers 응답 헤더
 * @param bytes 원본 본문 바이트
 */
public record ApiResponse(int status, JsonNode body, HttpHeaders headers, byte[] bytes) {
    /**
     * 오류 응답의 메시지
     *
     * @return {@code message} 값, 없으면 빈 문자열
     */
    public String message() {
        return body.path("message").asString("");
    }

    /**
     * 응답 헤더 값
     *
     * @return 첫 번째 값, 헤더가 없으면 빈 문자열
     */
    public String header(String name) {
        return headers.firstValue(name).orElse("");
    }
}
