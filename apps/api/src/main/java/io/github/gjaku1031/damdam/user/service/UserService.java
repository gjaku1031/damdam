package io.github.gjaku1031.damdam.user.service;

import static io.github.gjaku1031.damdam.jooq.Tables.USERS;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.dto.UserPage;
import io.github.gjaku1031.damdam.user.dto.UserResponse;
import io.github.gjaku1031.damdam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 운영자의 회원 조회와 댓글 작성 제한
 */
@Service
@RequiredArgsConstructor
public class UserService {
    /**
     * 회원 저장소
     */
    private final UserRepository users;

    /**
     * 목록·건수 조회용 jOOQ 문맥
     */
    private final DSLContext dsl;

    /**
     * 삭제되지 않은 회원 목록 조회
     *
     * 1. jOOQ로 조건에 맞는 쪽의 ID를 가입 최신순으로 조회
     * 2. 해당 ID의 엔티티를 JPA로 읽어 응답으로 변환
     * 3. 1의 순서대로 재정렬하고 전체 건수와 함께 반환
     *
     * @param paging 보정된 쪽 번호·크기
     * @param search 아이디 부분 일치 검색어, 대소문자 무시, {@code null}·빈 값이면 전체
     * @return 회원 목록
     */
    @Transactional(readOnly = true)
    public UserPage list(Paging paging, String search) {
        // 검색 조건: 삭제되지 않은 회원 중 아이디에 검색어 포함
        Condition condition = USERS.DELETEDAT.isNull();
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty()) {
            condition = condition.and(USERS.USERNAME.likeIgnoreCase("%" + escapeLike(keyword) + "%", '\\'));
        }
        // 정렬된 쪽 ID 조회
        var page = dsl.select(USERS.ID).from(USERS).where(condition)
                .orderBy(USERS.CREATEDAT.desc(), USERS.ID.desc())
                .limit(paging.size()).offset(paging.offset())
                .fetch(USERS.ID);
        // findAllById는 순서를 보장하지 않으므로 ID 순서로 다시 배열
        var byId = users.findAllById(page).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserResponse::from));
        return new UserPage(page.stream().map(byId::get).toList(), dsl.fetchCount(USERS, condition), paging.page(),
                paging.size());
    }

    /**
     * 회원 댓글 작성 제한
     *
     * 이미 제한된 회원은 최초 제한 기록 유지
     *
     * @param userId 대상 회원 ID
     * @param adminId 제한한 운영자 ID
     * @return 변경 후 회원 정보
     * @throws ApiException 회원이 없거나 삭제됐을 때(404)
     */
    @Transactional
    public UserResponse blockCommunity(UUID userId, UUID adminId) {
        UserEntity user = find(userId);
        user.blockCommunity(adminId);
        return UserResponse.from(users.saveAndFlush(user));
    }

    /**
     * 회원 댓글 작성 제한 해제
     *
     * @param userId 대상 회원 ID
     * @return 변경 후 회원 정보
     * @throws ApiException 회원이 없거나 삭제됐을 때(404)
     */
    @Transactional
    public UserResponse unblockCommunity(UUID userId) {
        UserEntity user = find(userId);
        user.unblockCommunity();
        return UserResponse.from(users.saveAndFlush(user));
    }

    /**
     * 삭제되지 않은 회원 조회
     *
     * @param userId 회원 ID
     * @return 회원 계정
     * @throws ApiException 없거나 삭제됐을 때(404)
     */
    private UserEntity find(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
    }

    /**
     * LIKE 특수문자 이스케이프
     *
     * @param value 검색어
     * @return {@code \}, {@code %}, {@code _} 앞에 {@code \}를 붙인 값
     */
    private static String escapeLike(String value) {
        return value.replaceAll("([\\\\%_])", "\\\\$1");
    }
}
