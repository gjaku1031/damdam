package io.github.gjaku1031.damdam.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴 요청
 *
 * @param password 본인 확인용 현재 비밀번호, 1~128자
 */
public record WithdrawRequest(@NotNull @Size(min = 1, max = 128) String password) {}
