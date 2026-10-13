package io.github.gjaku1031.damdam.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 시도 제한에 쓰는 클라이언트 IP 해석
 */
final class ClientIpTest {
    /**
     * 프록시를 신뢰하지 않으면 전달 헤더를 무시하고 접속 주소 사용(헤더 위조로 제한을 피할 수 없음)
     */
    @Test
    void ignoresForwardedHeaderWhenProxyIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(ClientIp.resolve(request, false)).isEqualTo("198.51.100.7");
    }

    /**
     * 프록시를 신뢰하면 프록시가 직접 덧붙인 마지막 주소 사용, 앞쪽 값은 클라이언트가 꾸밀 수 있음
     */
    @Test
    void usesLastForwardedHopWhenProxyIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");

        assertThat(ClientIp.resolve(request, true)).isEqualTo("203.0.113.9");
    }

    /**
     * 프록시를 신뢰해도 전달 헤더가 없거나 비었으면 접속 주소 사용
     */
    @Test
    void fallsBackToRemoteAddressWithoutForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");

        assertThat(ClientIp.resolve(request, true)).isEqualTo("198.51.100.7");
        request.addHeader("X-Forwarded-For", " ");
        assertThat(ClientIp.resolve(request, true)).isEqualTo("198.51.100.7");
    }
}
