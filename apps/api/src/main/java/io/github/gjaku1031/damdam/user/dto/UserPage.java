package io.github.gjaku1031.damdam.user.dto;

import java.util.List;

/**
 * 운영 회원 목록 응답 {@code {users, total, page, limit}}
 *
 * @param users 현재 쪽 회원, 가입 최신순
 * @param total 조건에 맞는 전체 회원 수
 * @param page 쪽 번호, 1부터 시작
 * @param limit 쪽 크기
 */
public record UserPage(List<UserResponse> users, long total, int page, int limit) {}
