package io.github.gjaku1031.damdam.fixture;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 PostgreSQL을 테스트 컨텍스트에 연결하고 종료 시 정리
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPostgresConfig {
    /**
     * 운영과 같은 PostgreSQL 16 테스트 인스턴스 생성
     *
     * 빈 DB에서 시작하므로 Flyway가 V1부터 모든 마이그레이션을 적용하고 Hibernate가 스키마를 검증함
     *
     * @return 개발 DB와 분리된 컨테이너, 시작·종료는 Spring 테스트 컨텍스트가 관리
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-bookworm");
    }
}
