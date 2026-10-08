package io.github.gjaku1031.damdam.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.auth.dto.AuthUserResponse;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.auth.LoginRequired;

/**
 * 로그인 회원 본인 정보 API
 */
@RestController
@LoginRequired
@RequestMapping("/users")
public final class UserController {
    /**
     * 현재 로그인 회원 프로필 조회
     *
     * @param user 현재 사용자
     * @return 로그인 사용자 프로필
     */
    @GetMapping("/me")
    public AuthUserResponse me(CurrentUser user) {
        return new AuthUserResponse(user.id(), user.username(), user.role());
    }
}
