package io.github.gjaku1031.damdam.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.auth.dto.AuthUserResponse;
import io.github.gjaku1031.damdam.auth.dto.ChangePasswordRequest;
import io.github.gjaku1031.damdam.auth.dto.LoginRequest;
import io.github.gjaku1031.damdam.auth.dto.RegisterRequest;
import io.github.gjaku1031.damdam.auth.dto.SessionResponse;
import io.github.gjaku1031.damdam.auth.dto.SignInResponse;
import io.github.gjaku1031.damdam.auth.dto.WithdrawRequest;
import io.github.gjaku1031.damdam.auth.service.AuthService;
import io.github.gjaku1031.damdam.auth.service.LoginRateLimiter;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.auth.LoginRequired;
import io.github.gjaku1031.damdam.common.auth.SessionUser;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.ClientIp;
import io.github.gjaku1031.damdam.config.DamdamProperties;
import io.github.gjaku1031.damdam.user.domain.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 가입·로그인·로그아웃, 세션 사용자 조회, 비밀번호 변경·탈퇴 API
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public final class AuthController {
    /**
     * 가입·로그인 서비스
     */
    private final AuthService auth;

    /**
     * 시도 횟수 제한기
     */
    private final LoginRateLimiter rateLimiter;

    /**
     * 프록시 신뢰 설정
     */
    private final DamdamProperties properties;

    /**
     * 고객 가입 후 바로 로그인
     *
     * @param body 가입 요청
     * @param request 현재 요청
     * @return 로그인 사용자 프로필
     * @throws ApiException 시도가 너무 많을 때(429), 아이디가 중복일 때(409)
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SignInResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        rateLimiter.check(LoginRateLimiter.Kind.REGISTER, clientIp(request), body.username());
        return signIn(request, auth.register(body));
    }

    /**
     * 아이디·비밀번호 로그인
     *
     * @param body 로그인 요청
     * @param request 현재 요청
     * @return 로그인 사용자 프로필
     * @throws ApiException 시도가 너무 많을 때(429), 아이디·비밀번호가 틀릴 때(401)
     */
    @PostMapping("/login")
    public SignInResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        rateLimiter.check(LoginRateLimiter.Kind.LOGIN, clientIp(request), body.username());
        UserEntity user = auth.authenticate(body.username(), body.password())
                .orElseThrow(() -> ApiException.unauthorized("아이디 또는 비밀번호가 일치하지 않습니다."));
        return signIn(request, user);
    }

    /**
     * 세션 사용자 프로필 조회
     *
     * @param request 현재 요청
     * @return 로그인 사용자 프로필
     * @throws ApiException 세션이 없거나 계정이 삭제됐을 때(401, 메시지 {@code Unauthorized})
     */
    @GetMapping("/me")
    public AuthUserResponse me(HttpServletRequest request) {
        return SessionUser.id(request).flatMap(auth::activeUser).map(AuthUserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized(null));
    }

    /**
     * 현재 세션 사용자 조회
     *
     * 비로그인도 오류 없이 200으로 응답해 브라우저 콘솔에 401 오류를 남기지 않음
     *
     * @param request 현재 요청
     * @return {@code {"user": 프로필}}, 세션이 없거나 계정이 삭제됐으면 {@code {"user": null}}
     */
    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        return new SessionResponse(SessionUser.id(request).flatMap(auth::activeUser).map(AuthUserResponse::from)
                .orElse(null));
    }

    /**
     * 비밀번호 변경
     *
     * 계정 상태를 바꾸는 명령이지만 로그인·로그아웃처럼 200으로 응답, 현재 세션은 유지
     *
     * @param body 비밀번호 변경 요청
     * @param user 현재 사용자
     * @return {@code {"success": true}}
     * @throws ApiException 현재 비밀번호가 틀릴 때(400)
     */
    @LoginRequired
    @PostMapping("/password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest body, CurrentUser user) {
        auth.changePassword(user.id(), body.currentPassword(), body.newPassword());
        return Map.of("success", true);
    }

    /**
     * 회원 탈퇴 후 로그아웃
     *
     * 로그인·로그아웃처럼 200으로 응답
     *
     * @param body 탈퇴 요청
     * @param user 현재 사용자
     * @param request 현재 요청
     * @return {@code {"success": true}}
     * @throws ApiException 비밀번호가 틀릴 때(400), 운영자일 때(403), 진행 중 주문이 있을 때(409)
     */
    @LoginRequired
    @PostMapping("/withdraw")
    public Map<String, Boolean> withdraw(@Valid @RequestBody WithdrawRequest body, CurrentUser user,
            HttpServletRequest request) {
        auth.withdraw(user.id(), body.password());
        // 탈퇴한 계정의 세션 폐기
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("success", true);
    }

    /**
     * 로그아웃
     *
     * 세션이 없어도 성공으로 응답
     *
     * @param request 현재 요청
     * @return {@code {"success": true}}
     */
    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        // 세션을 없애면 Spring Session이 만료된 쿠키를 내려보냄
        if (session != null) session.invalidate();
        return Map.of("success", true);
    }

    /**
     * 새 세션에 로그인 정보 저장
     *
     * 고정 세션 공격을 막기 위해 기존 세션을 폐기하고 로그인 때마다 새 세션 생성
     *
     * @param request 현재 요청
     * @param user 로그인할 계정
     * @return 로그인 사용자 프로필
     */
    private SignInResponse signIn(HttpServletRequest request, UserEntity user) {
        // 기존 세션 폐기 후 새 세션 발급
        HttpSession previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        HttpSession session = request.getSession(true);
        // 사용자 ID·권한 기록
        session.setAttribute(SessionUser.USER_ID, user.getId().toString());
        session.setAttribute(SessionUser.ROLE, user.getRole().name());
        return SignInResponse.of(AuthUserResponse.from(user));
    }

    /**
     * 시도 제한에 쓸 클라이언트 IP
     *
     * @param request 현재 요청
     * @return 프록시 신뢰 설정을 반영한 IP
     */
    private String clientIp(HttpServletRequest request) {
        return ClientIp.resolve(request, properties.trustProxy());
    }
}
