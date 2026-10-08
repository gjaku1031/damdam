package io.github.gjaku1031.damdam.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.type.LogicalType;

/**
 * JSON 직렬화·역직렬화 규칙
 *
 * <ul>
 *   <li>시각은 JavaScript {@code Date#toISOString()}과 같은 밀리초 UTC 문자열로 출력</li>
 *   <li>입력 타입 엄격 검사: 숫자→문자열, 문자열→숫자·불리언 같은 암묵 변환 거부</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {
    /**
     * 밀리초 UTC 시각 형식({@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'})
     */
    private static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * 시각 직렬화기와 암묵 타입 변환 거부 규칙 등록
     *
     * @return {@code JsonMapper} 빌더 설정
     */
    @Bean
    JsonMapperBuilderCustomizer damdamJson() {
        SimpleModule instants = new SimpleModule("damdam-instant").addSerializer(Instant.class, new MillisInstantSerializer());
        return builder -> builder
                .addModule(instants)
                // 문자열 필드: 숫자·불리언 입력 거부
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // 정수 필드: 문자열·불리언 입력 거부
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // 불리언 필드: 문자열·정수 입력 거부
                .withCoercionConfig(LogicalType.Boolean, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail));
    }

    /**
     * {@link Instant}를 밀리초 UTC 문자열로 쓰는 직렬화기
     */
    private static final class MillisInstantSerializer extends StdSerializer<Instant> {
        /**
         * {@link Instant} 전용 직렬화기 생성
         */
        MillisInstantSerializer() {
            super(Instant.class);
        }

        /**
         * 시각을 {@link #ISO_MILLIS} 형식 문자열로 출력
         *
         * @param value 직렬화할 시각
         * @param generator JSON 출력기
         * @param context 직렬화 문맥
         */
        @Override
        public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(ISO_MILLIS.format(value));
        }
    }
}
