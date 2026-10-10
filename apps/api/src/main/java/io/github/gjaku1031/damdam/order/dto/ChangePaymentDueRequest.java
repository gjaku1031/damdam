package io.github.gjaku1031.damdam.order.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * 운영자 입금 기한 변경 요청
 *
 * @param dueAt 새 입금 기한(ISO-8601), 지금 이후·최대 연장 범위 이내
 */
public record ChangePaymentDueRequest(@NotNull Instant dueAt) {}
