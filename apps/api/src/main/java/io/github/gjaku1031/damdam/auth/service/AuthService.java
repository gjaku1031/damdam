package io.github.gjaku1031.damdam.auth.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ORDERS;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.auth.dto.RegisterRequest;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import io.github.gjaku1031.damdam.user.domain.TermsConsentEntity;
import io.github.gjaku1031.damdam.user.domain.TermsEntity;
import io.github.gjaku1031.damdam.user.domain.TermsType;
import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.domain.UserRole;
import io.github.gjaku1031.damdam.user.repository.TermsConsentRepository;
import io.github.gjaku1031.damdam.user.repository.TermsRepository;
import io.github.gjaku1031.damdam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 아이디·비밀번호 계정의 가입과 로그인 검증
 */
@Service
@RequiredArgsConstructor
public final class AuthService {
    /**
     * 탈퇴를 막는 진행 중 주문 상태
     */
    private static final List<OrderStatus> IN_PROGRESS = List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
            OrderStatus.PROCESSING);

    /**
     * 회원 저장소
     */
    private final UserRepository users;

    /**
     * 약관 저장소
     */
    private final TermsRepository terms;

    /**
     * 약관 동의 저장소
     */
    private final TermsConsentRepository consents;

    /**
     * 비밀번호 해시 도구
     */
    private final PasswordHasher passwords;

    /**
     * 가입·비밀번호 변경·탈퇴 트랜잭션 실행기
     */
    private final TransactionTemplate transaction;

    /**
     * 진행 중 주문 확인용 jOOQ 문맥
     */
    private final DSLContext dsl;

    /**
     * 아이디·비밀번호 확인
     *
     * @param username 정규화된 아이디
     * @param password 평문 비밀번호
     * @return 일치하는 삭제되지 않은 계정, 없거나 비밀번호가 틀리면 빈 값
     */
    public Optional<UserEntity> authenticate(String username, String password) {
        return users.findByUsernameAndDeletedAtIsNull(username)
                .filter(user -> passwords.matches(user.getPasswordHash(), password));
    }

    /**
     * 고객 계정 생성과 필수 약관 두 개(서비스·개인정보) 동의 기록
     *
     * 해시는 트랜잭션 밖에서 계산, 계정·약관·동의 저장은 하나의 트랜잭션에서 실행되어 실패 시 함께 롤백됨
     *
     * 1. 비밀번호 해시 생성
     * 2. 계정 저장 후 즉시 flush(아이디 중복을 이 시점에 감지)
     * 3. 현재 버전 약관을 찾거나 만들고 동의 기록
     *
     * @param request 검증된 가입 요청
     * @return 저장된 계정
     * @throws ApiException 이미 사용 중인 아이디일 때(409)
     */
    public UserEntity register(RegisterRequest request) {
        // 느린 해시 계산은 트랜잭션·DB 연결을 잡기 전에 수행
        String hash = passwords.hash(request.password());
        try {
            return transaction.execute(status -> {
                UserEntity user = users.saveAndFlush(UserEntity.create(request.username(), hash, UserRole.CUSTOMER));
                for (TermsType type : new TermsType[] {TermsType.SERVICE, TermsType.PRIVACY}) {
                    TermsEntity current = terms.findByTypeAndVersion(type, TermsEntity.CURRENT_VERSION)
                            .orElseGet(() -> terms.save(TermsEntity.current(type)));
                    consents.save(TermsConsentEntity.of(user.getId(), current.getId()));
                }
                return user;
            });
        } catch (DataIntegrityViolationException error) {
            // 고유 제약 위반만 아이디 중복으로 변환, 그 밖의 무결성 오류는 그대로 전파
            if (isUniqueViolation(error)) throw ApiException.conflict("이미 사용 중인 아이디입니다.");
            throw error;
        }
    }

    /**
     * 비밀번호 변경
     *
     * 해시는 트랜잭션 밖에서 계산, 계정 재조회·현재 비밀번호 확인·저장은 하나의 트랜잭션에서 실행
     *
     * @param userId 로그인 사용자 ID
     * @param currentPassword 현재 비밀번호
     * @param newPassword 새 비밀번호, 12~128자로 검증된 값
     * @throws ApiException 현재 비밀번호가 틀릴 때(400, {@code fields.currentPassword}), 계정이 삭제됐을 때(401)
     */
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        // 느린 해시 계산은 트랜잭션·DB 연결을 잡기 전에 수행
        String hash = passwords.hash(newPassword);
        transaction.executeWithoutResult(status -> {
            UserEntity user = users.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> ApiException.unauthorized("다시 로그인해주세요."));
            if (!passwords.matches(user.getPasswordHash(), currentPassword)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "입력 내용을 확인해주세요.",
                        Map.of("currentPassword", "현재 비밀번호가 일치하지 않습니다."));
            }
            user.changePassword(hash);
        });
    }

    /**
     * 회원 탈퇴
     *
     * 하나의 트랜잭션에서 비밀번호·권한·진행 중 주문 확인 후 소프트 삭제, 실패 시 변경 없음
     *
     * @param userId 로그인 사용자 ID
     * @param password 본인 확인용 비밀번호
     * @throws ApiException 비밀번호가 틀릴 때(400, {@code fields.password}), 운영자일 때(403),
     *         결제 대기·결제 완료·처리 중 주문이 있을 때(409), 계정이 삭제됐을 때(401)
     */
    public void withdraw(UUID userId, String password) {
        transaction.executeWithoutResult(status -> {
            UserEntity user = users.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> ApiException.unauthorized("다시 로그인해주세요."));
            if (!passwords.matches(user.getPasswordHash(), password)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "입력 내용을 확인해주세요.",
                        Map.of("password", "비밀번호가 일치하지 않습니다."));
            }
            if (user.isAdmin()) throw ApiException.forbidden("운영자 계정은 탈퇴할 수 없습니다.");
            // 입금·제작·발송이 끝나지 않은 주문이 있으면 거부
            boolean inProgress = dsl.fetchExists(ORDERS, ORDERS.USERID.eq(userId)
                    .and(ORDERS.STATUS.in(IN_PROGRESS.stream().map(OrderStatus::name).toList())));
            if (inProgress) {
                throw ApiException.conflict("진행 중인 주문이 있으면 탈퇴할 수 없습니다. 주문을 마친 뒤 다시 시도해주세요.");
            }
            user.withdraw();
        });
    }

    /**
     * 삭제되지 않은 계정 조회
     *
     * @param id 사용자 ID
     * @return 계정, 없거나 삭제됐으면 빈 값
     */
    public Optional<UserEntity> activeUser(UUID id) {
        return users.findByIdAndDeletedAtIsNull(id);
    }

    /**
     * PostgreSQL 고유 제약 위반 여부
     *
     * @param error 검사할 예외, 원인 체인 전체를 확인
     * @return SQLSTATE {@code 23505}가 있으면 {@code true}
     */
    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
