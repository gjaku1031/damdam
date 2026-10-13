package io.github.gjaku1031.damdam.fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 브라우저 한 개처럼 세션 쿠키를 유지하는 테스트용 HTTP 클라이언트
 *
 * 상태를 바꾸는 요청은 웹과 같은 방식으로 CSRF 토큰을 받아 헤더에 실음
 * 클라이언트마다 다른 {@code X-Forwarded-For}를 보내 IP별 로그인·가입 시도 제한이 테스트끼리 겹치지 않게 함
 */
public final class ApiClient {
    /**
     * 테스트 서버가 허용하는 웹 출처
     */
    public static final String ORIGIN = "http://localhost:3000";

    /**
     * 클라이언트마다 다른 주소를 만들기 위한 순번
     */
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    /**
     * 쿠키 저장소를 가진 HTTP 클라이언트
     */
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    /**
     * {@code http://127.0.0.1:<포트>/api}
     */
    private final String baseUrl;

    /**
     * JSON 직렬화기
     */
    private final ObjectMapper json;

    /**
     * 이 클라이언트가 보내는 가상의 클라이언트 주소
     */
    private final String address;

    /**
     * 테스트 서버 포트와 JSON 직렬화기로 생성
     */
    public ApiClient(int port, ObjectMapper json) {
        this.baseUrl = "http://127.0.0.1:" + port + "/api";
        this.json = json;
        int sequence = NEXT_ADDRESS.incrementAndGet();
        this.address = "10." + (sequence >> 16 & 255) + "." + (sequence >> 8 & 255) + "." + (sequence & 255);
    }

    /**
     * 조회 요청
     */
    public ApiResponse get(String path) {
        return send("GET", path, null, Map.of());
    }

    /**
     * CSRF 토큰을 실은 생성·명령 요청
     *
     * @param body JSON으로 보낼 값, 본문이 없으면 {@code null}
     */
    public ApiResponse post(String path, Object body) {
        return send("POST", path, body, Map.of("X-CSRF-Token", csrfToken()));
    }

    /**
     * CSRF 토큰을 실은 부분 수정 요청
     */
    public ApiResponse patch(String path, Object body) {
        return send("PATCH", path, body, Map.of("X-CSRF-Token", csrfToken()));
    }

    /**
     * CSRF 토큰을 실은 삭제 요청
     */
    public ApiResponse delete(String path) {
        return send("DELETE", path, null, Map.of("X-CSRF-Token", csrfToken()));
    }

    /**
     * 현재 세션의 CSRF 토큰 조회
     *
     * 로그인·로그아웃으로 세션이 바뀌어도 맞는 값을 쓰도록 매번 서버에서 받음
     */
    public String csrfToken() {
        return get("/auth/csrf").body().path("token").asString();
    }

    /**
     * 아이디·비밀번호 로그인
     *
     * @return 로그인 응답
     */
    public ApiResponse login(String username, String password) {
        return post("/auth/login", Map.of("username", username, "password", password));
    }

    /**
     * 사진 한 장을 멀티파트로 업로드
     *
     * @param ticketId 업로드 티켓 ID
     * @param contentType 파일 파트의 MIME 형식
     */
    public ApiResponse upload(String ticketId, byte[] data, String contentType) {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + data.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(data, 0, body, head.length, data.length);
        System.arraycopy(tail, 0, body, head.length + data.length, tail.length);
        return exchange("POST", "/storage/upload/" + ticketId, HttpRequest.BodyPublishers.ofByteArray(body),
                Map.of("Content-Type", "multipart/form-data; boundary=" + boundary, "X-CSRF-Token", csrfToken()));
    }

    /**
     * 헤더를 직접 지정하는 요청
     *
     * CSRF 토큰을 자동으로 붙이지 않으므로 토큰이 없거나 틀린 요청, 다른 출처의 요청을 만들 때 사용
     *
     * @param body JSON으로 보낼 값, 본문이 없으면 {@code null}
     * @param headers 추가하거나 덮어쓸 헤더
     */
    public ApiResponse send(String method, String path, Object body, Map<String, String> headers) {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body instanceof String text ? text : json.writeValueAsString(body));
        return exchange(method, path, publisher, headers);
    }

    /**
     * 요청 전송과 응답 해석
     *
     * 기본 헤더(출처, JSON 본문 형식, 가상의 클라이언트 주소)를 넣은 뒤 전달받은 헤더로 덮어씀
     */
    private ApiResponse exchange(String method, String path, HttpRequest.BodyPublisher publisher,
            Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).method(method, publisher)
                .setHeader("Origin", ORIGIN)
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Forwarded-For", address);
        headers.forEach(request::setHeader);
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new ApiResponse(response.statusCode(), parse(response.body()), response.headers(), response.body());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /**
     * 응답 본문을 JSON으로 해석
     *
     * @return JSON 노드, 비었거나 JSON이 아니면(사진 등) 빈 객체
     */
    private JsonNode parse(byte[] body) {
        if (body.length == 0) return json.createObjectNode();
        try {
            return json.readTree(body);
        } catch (RuntimeException notJson) {
            return json.createObjectNode();
        }
    }
}
