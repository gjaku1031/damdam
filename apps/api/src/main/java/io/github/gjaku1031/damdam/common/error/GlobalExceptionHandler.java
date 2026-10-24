package io.github.gjaku1031.damdam.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 예외를 {@link ErrorResponse} 형식의 HTTP 오류 응답으로 변환
 *
 * 웹이 기대하는 상태 코드·메시지({@code 입력 내용을 확인해주세요.}, {@code Cannot GET /path} 등) 유지
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {
    /**
     * 입력 검증 실패 공통 메시지
     */
    private static final String INVALID_INPUT = "입력 내용을 확인해주세요.";

    /**
     * 예상하지 못한 예외 기록용 로거
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 업무 예외의 상태·메시지·필드를 그대로 응답
     *
     * @param error 업무 예외
     * @return 예외에 담긴 상태의 오류 응답
     */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException error) {
        return respond(error.getStatus(), error.getMessage(), error.getFields());
    }

    /**
     * 요청 본문 검증 실패를 필드별 메시지로 응답
     *
     * 같은 필드의 메시지가 여럿이면 공백으로 이어 붙임
     *
     * @param error 본문 검증 예외
     * @return 400 오류 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException error) {
        // 필드 오류는 입력 경로, 객체 수준 오류는 객체 이름을 키로 사용
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors()
                .forEach(field -> fields.merge(fieldPath(field), message(field), (a, b) -> a + " " + b));
        error.getBindingResult().getGlobalErrors()
                .forEach(global -> fields.merge(global.getObjectName(), String.valueOf(global.getDefaultMessage()),
                        (a, b) -> a + " " + b));
        return respond(HttpStatus.BAD_REQUEST, INVALID_INPUT, fields);
    }

    /**
     * 메서드 매개변수 검증 실패를 매개변수별 메시지로 응답
     *
     * @param error 매개변수 검증 예외
     * @return 400 오류 응답
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> invalidParameters(HandlerMethodValidationException error) {
        Map<String, String> fields = error.getParameterValidationResults().stream().collect(Collectors.toMap(
                result -> result.getMethodParameter().getParameterName(),
                result -> result.getResolvableErrors().stream().map(e -> String.valueOf(e.getDefaultMessage()))
                        .collect(Collectors.joining(" ")),
                (a, b) -> a + " " + b, LinkedHashMap::new));
        return respond(HttpStatus.BAD_REQUEST, INVALID_INPUT, fields);
    }

    /**
     * 읽을 수 없는 요청 본문 응답
     *
     * 선언하지 않은 속성은 {@code property X should not exist}, 타입 불일치는 형식 오류로 해당 경로에 표시
     *
     * @param error 본문 해석 예외
     * @return 400 오류 응답, 경로를 알 수 없으면 {@code fields} 생략
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (error.getCause() instanceof UnrecognizedPropertyException unknown) {
            fields.put(path(unknown), "property " + unknown.getPropertyName() + " should not exist");
        } else if (error.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            fields.put(path(jackson), "형식이 올바르지 않습니다.");
        }
        return respond(HttpStatus.BAD_REQUEST, INVALID_INPUT, fields.isEmpty() ? null : fields);
    }

    /**
     * 지원하지 않는 {@code Content-Type} 응답
     *
     * @return 400 오류 응답
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType() {
        return respond(HttpStatus.BAD_REQUEST, INVALID_INPUT, null);
    }

    /**
     * 경로 변수 타입 불일치 응답
     *
     * @return 404 오류 응답
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch() {
        // 잘못된 UUID 등 존재할 수 없는 식별자는 찾을 수 없는 자원으로 응답
        return respond(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), null);
    }

    /**
     * 없는 경로·허용하지 않는 메서드 응답
     *
     * @param request 현재 요청
     * @return 404 오류 응답, 메시지는 {@code Cannot <메서드> <경로>}
     */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErrorResponse> noRoute(HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Cannot " + request.getMethod() + " " + request.getRequestURI(), null);
    }

    /**
     * 업로드 크기 초과 응답
     *
     * @return 413 오류 응답
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge() {
        return respond(HttpStatus.CONTENT_TOO_LARGE, "File too large", null);
    }

    /**
     * 업로드 파일 누락·multipart 해석 실패 응답
     *
     * @return 400 오류 응답
     */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<ErrorResponse> missingFile() {
        return respond(HttpStatus.BAD_REQUEST, "사진을 선택해주세요.", null);
    }

    /**
     * 처리하지 못한 예외 기록 후 일반 오류 응답
     *
     * 내부 메시지는 응답에 노출하지 않음
     *
     * @param error 예상하지 못한 예외
     * @return 500 오류 응답
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception error) {
        log.error(error.getMessage(), error);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }

    /**
     * 오류 응답 조립
     *
     * @param status HTTP 상태
     * @param message 사용자 메시지
     * @param fields 입력 경로별 오류 메시지, 없으면 {@code null}
     * @return 상태와 본문을 담은 응답
     */
    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), message, fields));
    }

    /**
     * 필드 경로를 웹이 쓰는 점 표기로 변환
     *
     * @param field 필드 오류
     * @return {@code items[0].quantity}를 바꾼 {@code items.0.quantity} 형식 경로
     */
    private static String fieldPath(FieldError field) {
        return field.getField().replaceAll("\\[(\\d+)]", ".$1");
    }

    /**
     * 필드 오류 메시지
     *
     * @param field 필드 오류
     * @return 기본 메시지, 없으면 {@code <필드> is invalid}
     */
    private static String message(FieldError field) {
        return field.getDefaultMessage() == null ? field.getField() + " is invalid" : field.getDefaultMessage();
    }

    /**
     * Jackson 오류 위치를 점 경로로 변환
     *
     * @param error Jackson 예외
     * @return 속성 이름·배열 인덱스를 {@code .}으로 이은 경로
     */
    private static String path(JacksonException error) {
        return error.getPath().stream()
                .map(reference -> reference.getPropertyName() != null ? reference.getPropertyName()
                        : String.valueOf(reference.getIndex()))
                .collect(Collectors.joining("."));
    }
}
