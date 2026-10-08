package io.github.gjaku1031.damdam.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정리 작업 스케줄러 활성화
 *
 * {@code damdam.scheduling.enabled=false}인 일회성 명령(seed 등)과 테스트에서는 꺼짐
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "damdam.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
