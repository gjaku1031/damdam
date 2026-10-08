package io.github.gjaku1031.damdam.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * HTTP 상태와 사용자 메시지를 함께 담는 업무 예외
 *
 * {@link GlobalExceptionHandler}가 {@link ErrorResponse}로 변환
 * 메시지를 생략하면 예전 API와 같은 기본 문구({@code Not Found} 등 상태 이유 문구) 사용
 */
@Getter
public final class ApiException extends RuntimeException {
    /**
     * 응답 HTTP 상태
     */
    private final HttpStatus status;

    /**
     * 입력 경로별 오류 메시지, 없으면 {@code null}
     */
    private final Map<String, String> fields;

    /**
     * 필드 오류 없는 예외 생성
     *
     * @param status 응답 HTTP 상태
     * @param message 사용자 메시지, {@code null}이면 상태 이유 문구
     */
    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    /**
     * 필드 오류를 포함한 예외 생성
     *
     * @param status 응답 HTTP 상태
     * @param message 사용자 메시지, {@code null}이면 상태 이유 문구
     * @param fields 입력 경로별 오류 메시지, 없으면 {@code null}
     */
    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message == null ? status.getReasonPhrase() : message);
        this.status = status;
        this.fields = fields;
    }

    /**
     * 잘못된 요청 예외 생성
     *
     * @param message 사용자 메시지
     * @return 400 예외 객체
     */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 인증 필요 예외 생성
     *
     * @param message 사용자 메시지, {@code null}이면 {@code Unauthorized}
     * @return 401 예외 객체
     */
    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * 권한 없음 예외 생성
     *
     * @param message 사용자 메시지, {@code null}이면 {@code Forbidden}
     * @return 403 예외 객체
     */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    /**
     * 기본 문구의 권한 없음 예외 생성
     *
     * @return 메시지가 {@code Forbidden}인 403 예외 객체
     */
    public static ApiException forbidden() {
        return forbidden(null);
    }

    /**
     * 자원 없음 예외 생성
     *
     * @param message 사용자 메시지, {@code null}이면 {@code Not Found}
     * @return 404 예외 객체
     */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 기본 문구의 자원 없음 예외 생성
     *
     * @return 메시지가 {@code Not Found}인 404 예외 객체
     */
    public static ApiException notFound() {
        return notFound(null);
    }

    /**
     * 상태 충돌 예외 생성
     *
     * @param message 사용자 메시지
     * @return 409 예외 객체
     */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    /**
     * 외부 연동 실패 예외 생성
     *
     * @param message 사용자 메시지
     * @return 502 예외 객체
     */
    public static ApiException badGateway(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }

    /**
     * 일시적 서비스 불가 예외 생성
     *
     * @param message 사용자 메시지
     * @return 503 예외 객체
     */
    public static ApiException serviceUnavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
