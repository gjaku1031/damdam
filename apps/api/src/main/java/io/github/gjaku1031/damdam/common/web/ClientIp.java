package io.github.gjaku1031.damdam.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 IP 해석
 *
 * 프록시를 신뢰하면 {@code X-Forwarded-For}의 마지막 주소(바로 앞 프록시가 본 주소) 사용
 */
public final class ClientIp {
    /**
     * 정적 도우미 전용, 인스턴스 생성 금지
     */
    private ClientIp() {}

    /**
     * 클라이언트 IP 조회
     *
     * @param request 현재 요청
     * @param trustProxy {@code X-Forwarded-For}를 신뢰할지 여부
     * @return 신뢰하는 프록시 헤더의 마지막 주소, 아니면 소켓 원격 주소
     */
    public static String resolve(HttpServletRequest request, boolean trustProxy) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (trustProxy && forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
