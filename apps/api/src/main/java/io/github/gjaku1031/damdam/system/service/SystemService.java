package io.github.gjaku1031.damdam.system.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ADMIN_AUDIT_LOGS;
import static io.github.gjaku1031.damdam.jooq.Tables.USERS;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;
import io.github.gjaku1031.damdam.system.domain.AdminAuditLogEntity;
import io.github.gjaku1031.damdam.system.domain.SystemConfigEntity;
import io.github.gjaku1031.damdam.system.dto.AuditLogResponse;
import io.github.gjaku1031.damdam.system.dto.SiteInfoResponse;
import io.github.gjaku1031.damdam.system.dto.SystemConfigResponse;
import io.github.gjaku1031.damdam.system.repository.AdminAuditLogRepository;
import io.github.gjaku1031.damdam.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;

/**
 * 운영 기록(감사 로그)과 운영 설정
 */
@Service
@RequiredArgsConstructor
public class SystemService {
    /**
     * 비로그인 사용자에게 공개하는 설정 키
     */
    private static final List<String> PUBLIC_KEYS = List.of(SystemConfigEntity.BUSINESS_NAME,
            SystemConfigEntity.BUSINESS_OWNER, SystemConfigEntity.BUSINESS_NUMBER, SystemConfigEntity.MAIL_ORDER_NUMBER,
            SystemConfigEntity.BUSINESS_ADDRESS, SystemConfigEntity.CS_PHONE, SystemConfigEntity.CS_HOURS,
            SystemConfigEntity.CS_EMAIL, SystemConfigEntity.BANK_TRANSFER_ACCOUNT);

    /**
     * 운영 기록 저장소
     */
    private final AdminAuditLogRepository auditLogs;

    /**
     * 운영 설정 저장소
     */
    private final SystemConfigRepository configs;

    /**
     * 운영 기록 목록 조회용 jOOQ 문맥
     */
    private final DSLContext dsl;

    /**
     * 운영 기록 저장
     *
     * 호출자의 트랜잭션이 있으면 참여하여 업무 변경과 함께 커밋·롤백됨
     *
     * @param actorUserId 작업한 운영자 ID
     * @param action 작업 종류
     * @param targetType 대상 종류
     * @param targetId 대상 ID, 없으면 {@code null}
     * @param description 작업 설명
     */
    @Transactional
    public void log(UUID actorUserId, AdminAuditAction action, String targetType, String targetId, String description) {
        auditLogs.save(AdminAuditLogEntity.of(actorUserId, action, targetType, targetId, description));
    }

    /**
     * 운영 기록 목록 조회
     *
     * 작업자 아이디는 회원 테이블과 왼쪽 조인으로 함께 조회(삭제된 계정도 아이디 표시)
     *
     * @param paging 보정된 쪽 번호·크기
     * @param target 대상 종류, 대소문자 무시 일치, 비어 있으면 전체
     * @return 최신순 기록 목록
     */
    @Transactional(readOnly = true)
    public ItemPage<AuditLogResponse> auditLogs(Paging paging, String target) {
        // 대상 종류 조건
        Condition condition = target == null || target.isBlank() ? DSL.noCondition()
                : DSL.upper(ADMIN_AUDIT_LOGS.TARGETTYPE).eq(target.trim().toUpperCase(Locale.ROOT));
        // 쪽 단위 조회와 작업자 아이디 결합
        List<AuditLogResponse> items = dsl.select(ADMIN_AUDIT_LOGS.asterisk(), USERS.USERNAME)
                .from(ADMIN_AUDIT_LOGS)
                .leftJoin(USERS).on(USERS.ID.eq(ADMIN_AUDIT_LOGS.ACTOR_USER_ID))
                .where(condition)
                .orderBy(ADMIN_AUDIT_LOGS.CREATEDAT.desc(), ADMIN_AUDIT_LOGS.ID.desc())
                .limit(paging.size()).offset(paging.offset())
                .fetch(row -> new AuditLogResponse(row.get(ADMIN_AUDIT_LOGS.ID), row.get(ADMIN_AUDIT_LOGS.CREATEDAT),
                        row.get(ADMIN_AUDIT_LOGS.UPDATEDAT), row.get(ADMIN_AUDIT_LOGS.ACTOR_USER_ID),
                        row.get(USERS.USERNAME), AdminAuditAction.valueOf(row.get(ADMIN_AUDIT_LOGS.ACTION)),
                        row.get(ADMIN_AUDIT_LOGS.TARGETTYPE), row.get(ADMIN_AUDIT_LOGS.TARGETID),
                        row.get(ADMIN_AUDIT_LOGS.DESCRIPTION)));
        return ItemPage.of(items, dsl.fetchCount(ADMIN_AUDIT_LOGS, condition), paging);
    }

    /**
     * 공개 사업자·고객센터·입금 안내 조회
     *
     * @return 공개 설정 값, 없는 설정은 빈 문자열
     */
    @Transactional(readOnly = true)
    public SiteInfoResponse siteInfo() {
        return SiteInfoResponse.of(configs.findAllById(PUBLIC_KEYS).stream()
                .collect(Collectors.toMap(SystemConfigEntity::getConfigKey, SystemConfigEntity::getConfigValue)));
    }

    /**
     * 설정 값 조회
     *
     * @param key 설정 키
     * @return 설정 값, 설정이 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<String> config(String key) {
        return configs.findById(key).map(SystemConfigEntity::getConfigValue);
    }

    /**
     * 설정 저장
     *
     * 있으면 값·설명·수정자 갱신, 없으면 새로 추가 후 즉시 flush(응답의 수정 시각 반영)
     *
     * @param key 설정 키
     * @param value 설정 값
     * @param description 설정 설명, {@code null}이면 기존 설명 유지
     * @param updatedByUserId 수정한 운영자 ID
     * @return 저장 후 설정
     */
    @Transactional
    public SystemConfigResponse setConfig(String key, String value, String description, UUID updatedByUserId) {
        SystemConfigEntity config = configs.findById(key)
                .map(existing -> {
                    existing.update(value, description, updatedByUserId);
                    return existing;
                })
                .orElseGet(() -> configs.save(SystemConfigEntity.of(key, value, description, updatedByUserId)));
        configs.flush();
        return SystemConfigResponse.from(config);
    }

    /**
     * 전체 설정 조회
     *
     * @return 설정 키 오름차순 목록
     */
    @Transactional(readOnly = true)
    public List<SystemConfigResponse> allConfigs() {
        return configs.findAll(Sort.by("configKey")).stream().map(SystemConfigResponse::from).toList();
    }
}
