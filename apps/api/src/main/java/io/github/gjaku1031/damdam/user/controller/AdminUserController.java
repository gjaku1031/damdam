package io.github.gjaku1031.damdam.user.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.user.dto.UserPage;
import io.github.gjaku1031.damdam.user.dto.UserResponse;
import io.github.gjaku1031.damdam.user.service.UserService;
import lombok.RequiredArgsConstructor;

/**
 * 운영자용 회원 목록·댓글 제한 API
 *
 * {@code /api/admin/**} 경로라 운영자 권한 검사가 항상 적용됨
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public final class AdminUserController {
    /**
     * 회원 관리 서비스
     */
    private final UserService users;

    /**
     * 회원 목록 조회
     *
     * @param page 쪽 번호, 생략 시 1
     * @param limit 쪽 크기, 생략 시 20
     * @param search 아이디 검색어, 생략 시 전체
     * @return 회원 목록
     */
    @GetMapping
    public UserPage list(@RequestParam(required = false) String page, @RequestParam(required = false) String limit,
            @RequestParam(defaultValue = "") String search) {
        return users.list(Paging.of(page, limit, 20), search);
    }

    /**
     * 회원 댓글 작성 제한
     *
     * @param userId 대상 회원 ID
     * @param admin 현재 운영자
     * @return 변경 후 회원 정보
     */
    @PostMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse block(@PathVariable UUID userId, CurrentUser admin) {
        return users.blockCommunity(userId, admin.id());
    }

    /**
     * 회원 댓글 작성 제한 해제
     *
     * @param userId 대상 회원 ID
     * @return 변경 후 회원 정보
     */
    @PostMapping("/{userId}/unblock")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse unblock(@PathVariable UUID userId) {
        return users.unblockCommunity(userId);
    }
}
