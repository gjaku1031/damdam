package io.github.gjaku1031.damdam.config;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * {@code .env}와 환경 변수 규약을 Spring 설정으로 변환
 *
 * <ul>
 *   <li>로컬 개발: 작업 디렉터리의 {@code .env}, 저장소 루트의 {@code ../../.env}를 가장 낮은 우선순위로 읽음</li>
 *   <li>DB: {@code DATABASE_URL}(postgresql://) 또는 {@code DB_*} 값으로 JDBC URL·계정 구성</li>
 * </ul>
 */
public final class DamdamEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /**
     * {@code .env} 파일과 파생 DB 설정을 속성 원본으로 추가
     *
     * 1. {@code .env} 파일을 읽어 가장 낮은 우선순위로 추가
     * 2. {@code DATABASE_URL} 또는 {@code DB_*} 값으로 {@code spring.datasource.*} 구성
     * 3. 파생 설정을 시스템 환경 변수 바로 뒤에 추가(명시적 {@code SPRING_*} 설정이 우선)
     *
     * @param environment 수정할 환경
     * @param application 실행 중인 애플리케이션
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> dotenv = new LinkedHashMap<>();
        // 먼저 읽은 파일이 우선
        for (Path file : new Path[] {Path.of(".env"), Path.of("../../.env")}) {
            readDotenv(file).forEach(dotenv::putIfAbsent);
        }
        if (!dotenv.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("dotenv", dotenv));
        }

        // DB 접속 정보: DATABASE_URL이 있으면 URL에서, 없으면 DB_* 개별 값에서 구성
        Map<String, Object> derived = new HashMap<>();
        String databaseUrl = environment.getProperty("DATABASE_URL");
        boolean ssl = "true".equals(environment.getProperty("DB_SSL"));
        if (StringUtils.hasText(databaseUrl)) {
            URI uri = URI.create(databaseUrl);
            String query = uri.getRawQuery();
            derived.put("spring.datasource.url", jdbcUrl(uri.getHost(), uri.getPort() < 0 ? 5432 : uri.getPort(),
                    uri.getPath().replaceFirst("^/", ""), query, ssl));
            String userInfo = uri.getRawUserInfo();
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                derived.put("spring.datasource.username", decode(parts[0]));
                derived.put("spring.datasource.password", parts.length > 1 ? decode(parts[1]) : "");
            }
        } else {
            derived.put("spring.datasource.url", jdbcUrl(environment.getProperty("DB_HOST", "localhost"),
                    Integer.parseInt(environment.getProperty("DB_PORT", "5432")),
                    environment.getProperty("DB_NAME", "damdam"), null, ssl));
            derived.put("spring.datasource.username", environment.getProperty("DB_USER", "damdam"));
            derived.put("spring.datasource.password", environment.getProperty("DB_PASSWORD", ""));
        }
        // SPRING_DATASOURCE_* 같은 명시적 설정이 있으면 그 값이 우선
        environment.getPropertySources().addAfter("systemEnvironment", new MapPropertySource("damdamDerived", derived));
    }

    /**
     * PostgreSQL JDBC URL 조립
     *
     * @param host DB 호스트
     * @param port DB 포트
     * @param database DB 이름
     * @param query 원래 URL의 쿼리 문자열, 없으면 {@code null}
     * @param ssl {@code true}이고 {@code sslmode}가 없으면 {@code sslmode=verify-full} 추가
     * @return {@code jdbc:postgresql://} URL
     */
    private static String jdbcUrl(String host, int port, String database, String query, boolean ssl) {
        StringBuilder url = new StringBuilder("jdbc:postgresql://").append(host).append(':').append(port).append('/')
                .append(database);
        String params = query == null ? "" : query;
        if (ssl && !params.contains("sslmode=")) {
            params = params.isEmpty() ? "sslmode=verify-full" : params + "&sslmode=verify-full";
        }
        if (!params.isEmpty()) url.append('?').append(params);
        return url.toString();
    }

    /**
     * URL 인코딩된 계정 정보 복원
     *
     * @param value 퍼센트 인코딩된 값
     * @return UTF-8로 복원한 값
     */
    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * {@code .env} 파일의 {@code KEY=VALUE} 목록 읽기
     *
     * 빈 줄·{@code #} 주석·키 없는 줄은 건너뛰고, 값을 감싼 작은따옴표·큰따옴표는 제거
     *
     * @param file 읽을 파일
     * @return 파일 순서를 유지한 값, 파일이 없거나 읽을 수 없으면 빈 맵
     */
    private static Map<String, String> readDotenv(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                int separator = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || separator < 1) continue;
                // 값 양끝의 같은 따옴표 한 쌍 제거
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(trimmed.substring(0, separator).trim(), value);
            }
        } catch (IOException ignored) {
            // 읽을 수 없는 로컬 .env는 무시하고 실제 환경 변수만 사용
        }
        return values;
    }

    /**
     * 후처리기 실행 순서
     *
     * @return 가장 낮은 우선순위(다른 후처리기 이후 실행)
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
