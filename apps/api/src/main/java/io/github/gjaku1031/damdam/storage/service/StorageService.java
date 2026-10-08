package io.github.gjaku1031.damdam.storage.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ADMIN_UPDATES;
import static io.github.gjaku1031.damdam.jooq.Tables.CMS_CONTENTS;
import static io.github.gjaku1031.damdam.jooq.Tables.CMS_POST_IMAGES;
import static io.github.gjaku1031.damdam.jooq.Tables.ORDERS;
import static io.github.gjaku1031.damdam.jooq.Tables.ORDER_ITEMS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_IMAGES;
import static io.github.gjaku1031.damdam.jooq.Tables.STORAGE_UPLOAD_TICKETS;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.selectOne;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.config.DamdamProperties;
import io.github.gjaku1031.damdam.feed.domain.PostState;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketEntity;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketStatus;
import io.github.gjaku1031.damdam.storage.domain.UploadDomain;
import io.github.gjaku1031.damdam.storage.dto.UploadTicketResponse;
import io.github.gjaku1031.damdam.storage.repository.StorageUploadTicketRepository;
import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 사진 업로드와 제공
 *
 * 업로드 2단계
 * 1. 티켓 발급: 형식·크기를 미리 신고하고 저장 키와 만료 시각(30분)을 받음
 * 2. 파일 업로드: 실제 바이트의 서명·형식·크기가 티켓과 같을 때만 저장하고 티켓을 검증 완료로 변경
 *
 * 사진 접근 권한 판단
 * 1. 운영자면 모든 사진 허용
 * 2. 공개된 게시글·상품·공지/FAQ 본문에 쓰인 사진이면 허용
 * 3. 로그인 사용자 본인 주문의 대표 사진이면 허용
 * 4. 그 외에는 존재 여부를 숨기려고 404로 응답
 */
@Service
@RequiredArgsConstructor
public class StorageService {
    /**
     * 업로드 사진 최대 크기(바이트, 5MB)
     */
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    /**
     * 업로드 티켓 유효 시간
     */
    private static final Duration TICKET_TTL = Duration.ofMinutes(30);

    /**
     * 허용 MIME 형식별 저장 파일 확장자
     */
    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    /**
     * 기존 정적 사진 파일명 형식
     */
    private static final Pattern LEGACY_FILENAME = Pattern.compile("^[\\w./-]+\\.(jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 업로드 티켓 저장소
     */
    private final StorageUploadTicketRepository tickets;

    /**
     * 운영자 여부 확인용 사용자 저장소
     */
    private final UserRepository users;

    /**
     * 업로드 파일 저장소
     */
    private final FileStorage files;

    /**
     * 사진 사용처 확인·티켓 일괄 실패 처리용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 기존 정적 사진 경로 설정
     */
    private final DamdamProperties properties;

    /**
     * 업로드 티켓 발급
     *
     * @param userId 발급받는 운영자 ID
     * @param domain 사진을 쓸 도메인, 저장 키 접두사로 사용
     * @param contentType 업로드할 MIME 형식(JPG·PNG·WebP)
     * @param sizeBytes 업로드할 파일 크기(1바이트~5MB)
     * @return 저장 키와 만료 시각을 담은 티켓
     * @throws ApiException 형식이 허용되지 않거나 크기가 범위를 벗어날 때(400)
     */
    @Transactional
    public UploadTicketResponse issueTicket(UUID userId, UploadDomain domain, String contentType, int sizeBytes) {
        // 형식·크기 검증
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw ApiException.badRequest("사진 형식과 크기를 확인해주세요.");
        }
        // 도메인 접두사와 임의 UUID로 저장 키를 만들어 티켓 저장
        String objectKey = domain.name().toLowerCase() + "/" + UUID.randomUUID() + extension;
        StorageUploadTicketEntity ticket = tickets.saveAndFlush(
                StorageUploadTicketEntity.issue(userId, domain, objectKey, sizeBytes, contentType, TICKET_TTL));
        return UploadTicketResponse.from(ticket);
    }

    /**
     * 티켓으로 파일 업로드
     *
     * 티켓 행을 잠가 같은 티켓의 동시 업로드를 막음
     * 파일 저장 후 트랜잭션이 실패하면 티켓은 롤백되지만 저장한 파일은 남음
     *
     * 1. 티켓 잠금 조회 후 발급자·사용 가능 여부 확인
     * 2. 바이트 서명, 신고한 형식·크기와 실제 값 비교
     * 3. 파일 저장 후 티켓을 검증 완료로 변경
     *
     * @param data 업로드한 파일 바이트
     * @param contentType 요청이 신고한 파일 MIME 형식
     * @param userId 업로드하는 운영자 ID
     * @return {@code {objectKey}}
     * @throws ApiException 티켓이 없을 때(404), 발급자가 아닐 때(403), 만료·사용했거나 형식·크기가 다를 때(400)
     * @throws UncheckedIOException 파일 저장 실패
     */
    @Transactional
    public Map<String, String> upload(UUID ticketId, byte[] data, String contentType, UUID userId) {
        // 티켓 잠금 조회와 발급자·상태 확인
        StorageUploadTicketEntity ticket = tickets.findForUpdate(ticketId).orElseThrow(ApiException::notFound);
        if (!ticket.getIssuedByUserId().equals(userId)) throw ApiException.forbidden();
        if (!ticket.isUsable(Instant.now())) throw ApiException.badRequest("만료되었거나 사용한 업로드입니다.");
        // 실제 바이트가 티켓에 신고한 사진과 같은지 확인
        if (!hasImageSignature(data, contentType) || !contentType.equals(ticket.getExpectedContentType())
                || data.length != ticket.getExpectedSizeBytes() || data.length > MAX_BYTES) {
            throw ApiException.badRequest("사진 형식 또는 실제 크기가 다릅니다.");
        }
        // 파일 저장 후 티켓 검증 완료
        try {
            files.save(ticket.getObjectKey(), data);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        ticket.markVerified();
        return Map.of("objectKey", ticket.getObjectKey());
    }

    /**
     * 본문·상품에 연결할 사진 확인
     *
     * 검증을 마친 업로드 사진이거나 상위 경로 이동이 없는 기존 정적 사진이어야 함
     *
     * @param key 업로드 사진 키 또는 {@code /images/...} 경로
     * @throws ApiException 검증을 마친 업로드가 아닐 때(400)
     */
    @Transactional(readOnly = true)
    public void assertUsable(String key) {
        if (key.startsWith(ImageKeys.LEGACY_PREFIX) && !key.contains("..")) return;
        if (!tickets.existsByObjectKeyAndStatus(key, StorageUploadTicketStatus.VERIFIED)) {
            throw ApiException.badRequest("완료된 업로드를 사용해주세요.");
        }
    }

    /**
     * 업로드 사진 바이트 조회
     *
     * @param key {@code post|product/<uuid>.<ext>} 형식의 저장 키
     * @param userId 세션 사용자 ID, 비로그인이면 빈 값
     * @return 파일 바이트
     * @throws ApiException 형식이 다르거나 접근할 수 없거나 파일이 없을 때(404)
     */
    @Transactional(readOnly = true)
    public byte[] file(String key, Optional<UUID> userId) {
        // 키 형식과 접근 권한을 먼저 확인해 파일 존재 여부 노출 방지
        if (!ImageKeys.UPLOADED.matcher(key).matches()) throw ApiException.notFound();
        authorize(key, userId);
        if (!files.exists(key)) throw ApiException.notFound();
        try {
            return files.read(key);
        } catch (IOException error) {
            throw ApiException.notFound();
        }
    }

    /**
     * 웹 공개 폴더에서 옮겨 온 기존 사진({@code /images/...}) 바이트 조회
     *
     * @param filename {@code /images/} 아래 상대 경로
     * @param userId 세션 사용자 ID, 비로그인이면 빈 값
     * @return 파일 바이트
     * @throws ApiException 형식이 다르거나 기준 경로를 벗어나거나 접근할 수 없거나 파일이 없을 때(404)
     */
    @Transactional(readOnly = true)
    public byte[] legacyFile(String filename, Optional<UUID> userId) {
        // 파일명 형식·경로 이동 검사 후 접근 권한 확인
        if (!LEGACY_FILENAME.matcher(filename).matches() || filename.contains("..") || filename.startsWith("/")) {
            throw ApiException.notFound();
        }
        authorize(ImageKeys.LEGACY_PREFIX + filename, userId);
        // 기준 경로 밖으로 벗어나는 경로 거부 후 읽기
        Path root = Path.of(properties.storage().legacyImagesPath()).toAbsolutePath().normalize();
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root)) throw ApiException.notFound();
        try {
            return Files.readAllBytes(file);
        } catch (IOException missing) {
            throw ApiException.notFound();
        }
    }

    /**
     * 만료될 때까지 업로드하지 않은 티켓 일괄 실패 처리
     *
     * @return 실패 처리한 티켓 수
     */
    @Transactional
    public int failExpiredTickets() {
        Instant now = Instant.now();
        return dsl.update(STORAGE_UPLOAD_TICKETS)
                .set(STORAGE_UPLOAD_TICKETS.STATUS, StorageUploadTicketStatus.FAILED.name())
                .set(STORAGE_UPLOAD_TICKETS.FAILEDAT, now)
                .set(STORAGE_UPLOAD_TICKETS.UPDATEDAT, now)
                .where(STORAGE_UPLOAD_TICKETS.STATUS.eq(StorageUploadTicketStatus.ISSUED.name()))
                .and(STORAGE_UPLOAD_TICKETS.EXPIRESAT.le(now))
                .execute();
    }

    /**
     * 사진 접근 권한 확인
     *
     * 1. 삭제되지 않은 운영자면 통과
     * 2. 공개 사용처 또는 본인 주문 사용처가 하나라도 있으면 통과
     *
     * @param key 업로드 사진 키 또는 {@code /images/...} 경로
     * @param userId 세션 사용자 ID, 비로그인이면 빈 값
     * @throws ApiException 접근할 수 없을 때(404)
     */
    private void authorize(String key, Optional<UUID> userId) {
        Optional<UserEntity> user = userId.flatMap(users::findByIdAndDeletedAtIsNull);
        if (user.map(UserEntity::isAdmin).orElse(false)) return;
        // 공개 사용처와 본인 주문 사용처를 한 번의 EXISTS 조회로 확인
        if (!dsl.fetchExists(selectOne().where(publiclyUsed(key).or(orderedBy(key, user.map(UserEntity::getId)))))) {
            throw ApiException.notFound();
        }
    }

    /**
     * 공개 사용처 조건
     *
     * 발행·공개된 게시글 사진, 공개 상품 사진, 공개 공지/FAQ 본문의 {@code src} 중 하나
     *
     * @return 사진이 공개 사용처에 쓰였으면 참인 조건
     */
    private Condition publiclyUsed(String key) {
        String src = "%src=\"" + ImageKeys.publicUrl(key) + "\"%";
        return exists(selectOne().from(CMS_POST_IMAGES).join(ADMIN_UPDATES).on(ADMIN_UPDATES.ID.eq(CMS_POST_IMAGES.POST_ID))
                        .where(CMS_POST_IMAGES.OBJECTKEY.eq(key), ADMIN_UPDATES.VISIBLE.isTrue(),
                                ADMIN_UPDATES.STATE.eq(PostState.PUBLISHED.name()), ADMIN_UPDATES.DELETEDAT.isNull()))
                .or(exists(selectOne().from(PRODUCT_IMAGES).join(PRODUCTS).on(PRODUCTS.ID.eq(PRODUCT_IMAGES.PRODUCTID))
                        .where(PRODUCT_IMAGES.OBJECTKEY.eq(key), PRODUCTS.VISIBLE.isTrue(), PRODUCTS.DELETEDAT.isNull())))
                .or(exists(selectOne().from(CMS_CONTENTS)
                        .where(CMS_CONTENTS.VISIBLE.isTrue(), CMS_CONTENTS.DELETEDAT.isNull(), CMS_CONTENTS.BODYHTML.like(src))));
    }

    /**
     * 본인 주문 사용처 조건
     *
     * 숨김·삭제된 상품이어도 본인 주문 내역의 대표 사진은 허용
     *
     * @param userId 사용자 ID, 비로그인이면 빈 값
     * @return 본인 주문의 대표 사진이면 참인 조건, 비로그인이면 항상 거짓
     */
    private Condition orderedBy(String key, Optional<UUID> userId) {
        return userId.map(id -> exists(selectOne().from(ORDER_ITEMS).join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDERID))
                        .where(ORDER_ITEMS.THUMBNAILIMAGEURLSNAPSHOT.eq(key), ORDERS.USERID.eq(id))))
                .orElse(falseCondition());
    }

    /**
     * 파일 앞부분 서명이 신고한 사진 형식과 일치하는지 확인
     *
     * @param contentType JPG·PNG·WebP MIME 형식, 그 외는 항상 거짓
     * @return 서명 일치 여부
     */
    private static boolean hasImageSignature(byte[] data, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(data, 0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(data, 0, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            case "image/webp" -> startsWith(data, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && startsWith(data, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    /**
     * 지정 위치의 바이트가 서명과 같은지 확인
     *
     * @param offset 비교 시작 위치
     * @return 일치 여부, 데이터가 짧으면 거짓
     */
    private static boolean startsWith(byte[] data, int offset, byte[] signature) {
        return data.length >= offset + signature.length
                && Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
    }
}
