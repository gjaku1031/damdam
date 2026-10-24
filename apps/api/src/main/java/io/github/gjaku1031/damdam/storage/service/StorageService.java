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
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.selectOne;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.feed.domain.PostState;
import io.github.gjaku1031.damdam.storage.domain.ImageVariant;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketEntity;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketStatus;
import io.github.gjaku1031.damdam.storage.domain.UploadDomain;
import io.github.gjaku1031.damdam.storage.dto.StoredImage;
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
 * 2. 파일 업로드: 실제 바이트의 서명·형식·크기가 티켓과 같을 때만 원본과 크기별 변환본({@link ImageVariant})을 저장하고
 *    티켓을 검증 완료로 변경
 *
 * 사진 제공
 * <ul>
 *   <li>요청한 크기의 변환본을 보냄, 크기를 지정하지 않으면 대({@link ImageVariant#LARGE})</li>
 *   <li>변환본이 없는 사진(변환기 없는 환경에서 올렸거나 일괄 변환 전)만 원본을 보냄</li>
 *   <li>원본에는 촬영 위치 등 메타데이터가 남아 있으므로 변환본이 있으면 원본을 내보내지 않음</li>
 * </ul>
 *
 * 쓰이지 않는 업로드 정리
 * <ul>
 *   <li>어디에도 연결되지 않은 채 {@link #ORPHAN_GRACE}가 지난 업로드의 원본·변환본·티켓을 삭제</li>
 *   <li>사진은 먼저 올린 뒤 상품·글을 저장할 때 연결되므로, 올린 직후의 사진을 지우지 않도록 유예 시간을 둠</li>
 *   <li>사진을 연결하는 쪽은 {@link #assertUsable}로 티켓을 공유 잠금, 정리하는 쪽은 배타 잠금 후 다시 확인</li>
 * </ul>
 *
 * 사진 접근 권한 판단
 * 1. 공개된 게시글·상품·공지/FAQ 본문에 쓰인 사진이면 허용, 누구에게나 같은 응답이므로 캐시 허용
 * 2. 운영자면 모든 사진 허용
 * 3. 로그인 사용자 본인 주문의 대표 사진이면 허용
 * 4. 그 외에는 존재 여부를 숨기려고 404로 응답
 */
@Service
@RequiredArgsConstructor
public class StorageService {
    /**
     * 파일 삭제 실패 로거
     */
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /**
     * 업로드 사진 최대 크기(바이트, 5MB)
     */
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    /**
     * 업로드 티켓 유효 시간
     */
    private static final Duration TICKET_TTL = Duration.ofMinutes(30);

    /**
     * 연결되지 않은 업로드를 지우기 전에 기다리는 시간
     *
     * 사진을 올린 뒤 상품·글을 저장하기 전까지는 사용처가 없으므로, 편집 중인 사진이 지워지지 않을 만큼의 여유
     */
    private static final Duration ORPHAN_GRACE = Duration.ofHours(24);

    /**
     * 허용 MIME 형식별 저장 파일 확장자
     */
    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

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
     * 크기별 변환본 생성기
     */
    private final ImageConverter converter;

    /**
     * 변환이 끝난 뒤의 저장 구간, 티켓 하나의 삭제 구간만 묶는 트랜잭션 실행기
     */
    private final TransactionTemplate transaction;

    /**
     * 사진 사용처 확인·티켓 일괄 실패 처리·정리 대상 조회용 jOOQ
     */
    private final DSLContext dsl;

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
     * 변환은 1초 안팎이 걸리므로 DB 트랜잭션과 티켓 잠금 밖에서 수행
     * 파일 저장 후 트랜잭션이 실패하면 티켓은 롤백되지만 저장한 파일은 남음
     *
     * 1. 티켓의 발급자·사용 가능 여부, 바이트 서명·형식·크기 확인(잠금 없음)
     * 2. 크기별 변환본 생성, 변환기가 없는 환경에서는 생략
     * 3. 티켓 행을 잠가 같은 티켓의 동시 업로드를 막고 다시 확인한 뒤 원본·변환본 저장, 티켓을 검증 완료로 변경
     *
     * @param data 업로드한 파일 바이트
     * @param contentType 요청이 신고한 파일 MIME 형식
     * @param userId 업로드하는 운영자 ID
     * @return {@code {objectKey}}
     * @throws ApiException 티켓이 없을 때(404), 발급자가 아닐 때(403), 만료·사용했거나 형식·크기가 다르거나 사진을 변환하지 못할 때(400)
     * @throws UncheckedIOException 파일 저장 실패
     */
    public Map<String, String> upload(UUID ticketId, byte[] data, String contentType, UUID userId) {
        // 잘못된 요청에 변환 비용을 쓰지 않도록 먼저 확인
        assertUploadable(tickets.findById(ticketId).orElseThrow(ApiException::notFound), data, contentType, userId);
        Map<ImageVariant, byte[]> variants = convert(data);
        // 변환하는 동안 다른 요청이 같은 티켓을 썼을 수 있으므로 잠근 뒤 다시 확인
        return transaction.execute(status -> {
            StorageUploadTicketEntity ticket = tickets.findForUpdate(ticketId).orElseThrow(ApiException::notFound);
            assertUploadable(ticket, data, contentType, userId);
            try {
                files.save(ticket.getObjectKey(), data);
                for (Map.Entry<ImageVariant, byte[]> variant : variants.entrySet()) {
                    files.save(ImageKeys.variantKey(ticket.getObjectKey(), variant.getKey()), variant.getValue());
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            ticket.markVerified();
            return Map.of("objectKey", ticket.getObjectKey());
        });
    }

    /**
     * 본문·상품에 연결할 사진 확인
     *
     * 검증을 마친 업로드 사진이어야 함
     * 업로드 사진은 티켓을 공유 잠금으로 읽음. 사진을 연결하는 트랜잭션 안에서 호출하면 그 트랜잭션이 끝날 때까지
     * {@link #deleteIfOrphan}이 같은 사진을 지우지 못하고, 이미 지워진 사진이면 여기서 거부됨
     * 트랜잭션 밖에서 호출하면 잠금이 바로 풀리므로 사전 확인으로만 쓰고, 연결하는 트랜잭션 안에서 한 번 더 호출해야 함
     *
     * @param key 업로드 사진 키
     * @throws ApiException 검증을 마친 업로드가 아닐 때(400)
     */
    @Transactional
    public void assertUsable(String key) {
        if (tickets.findForShare(key, StorageUploadTicketStatus.VERIFIED).isEmpty()) {
            throw ApiException.badRequest("완료된 업로드를 사용해주세요.");
        }
    }

    /**
     * 업로드 사진 조회
     *
     * @param key {@code post|product/<uuid>.<ext>} 형식의 저장 키
     * @param userId 세션 사용자 ID, 비로그인이면 빈 값
     * @param size 변환본 크기({@code s}·{@code m}·{@code l}), {@code null}이면 대
     * @return 요청한 크기의 변환본, 변환본이 없는 사진은 원본
     * @throws ApiException 키·크기 형식이 다르거나 접근할 수 없거나 파일이 없을 때(404)
     */
    @Transactional(readOnly = true)
    public StoredImage file(String key, Optional<UUID> userId, String size) {
        // 키 형식과 접근 권한을 먼저 확인해 파일 존재 여부 노출 방지
        if (!ImageKeys.UPLOADED.matcher(key).matches()) throw ApiException.notFound();
        ImageVariant variant = variantOf(size);
        boolean cacheable = authorize(key, userId);
        Optional<byte[]> converted = variantBytes(key, variant);
        if (converted.isPresent()) return new StoredImage(converted.get(), ImageKeys.VARIANT_CONTENT_TYPE, cacheable);
        // 변환본이 없는 사진만 원본 제공
        if (!files.exists(key)) throw ApiException.notFound();
        try {
            return new StoredImage(files.read(key), ImageKeys.contentType(key), cacheable);
        } catch (IOException error) {
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
     * 정리 대상 업로드 티켓 ID 조회
     *
     * 잠금 없이 후보만 고름, 실제 삭제 여부는 {@link #deleteIfOrphan}이 티켓을 잠근 뒤 다시 판단
     *
     * @param limit 최대 건수
     * @return 업로드·실패 처리 후 {@link #ORPHAN_GRACE}가 지났고 사용처가 없는 티켓 ID, 오래된 순
     */
    @Transactional(readOnly = true)
    public List<UUID> orphanTicketIds(int limit) {
        return dsl.select(STORAGE_UPLOAD_TICKETS.ID).from(STORAGE_UPLOAD_TICKETS)
                .where(orphan(Instant.now().minus(ORPHAN_GRACE)))
                .orderBy(STORAGE_UPLOAD_TICKETS.CREATEDAT, STORAGE_UPLOAD_TICKETS.ID)
                .limit(limit)
                .fetch(STORAGE_UPLOAD_TICKETS.ID);
    }

    /**
     * 쓰이지 않는 업로드 하나 삭제
     *
     * 1. 티켓 행을 배타 잠금(사진을 연결하는 트랜잭션의 공유 잠금이 끝날 때까지 기다림)
     * 2. 잠근 뒤에도 유예 시간이 지났고 사용처가 없는지 다시 확인, 그사이 연결됐으면 아무것도 지우지 않음
     * 3. 티켓 행 삭제 후 커밋(이후 이 사진을 연결하려는 요청은 {@link #assertUsable}에서 거부됨)
     * 4. 커밋된 뒤 원본과 변환본 파일 삭제
     *
     * 파일을 먼저 지우면 티켓 삭제가 롤백됐을 때 연결 가능한 티켓에 파일이 없는 상태가 되므로 행을 먼저 지움
     * 4단계가 실패하면 티켓 없는 파일이 남고 오류 로그만 남김(사진으로는 더 이상 연결할 수 없음)
     *
     * @return 삭제했으면 {@code true}, 티켓이 없거나 그사이 연결됐으면 {@code false}
     */
    public boolean deleteIfOrphan(UUID ticketId) {
        String key = transaction.execute(status -> {
            StorageUploadTicketEntity ticket = tickets.findForUpdate(ticketId).orElse(null);
            if (ticket == null) return null;
            // 후보로 고른 뒤 잠그기 전에 상품·글에 연결됐을 수 있으므로 같은 조건으로 재확인
            boolean stillOrphan = dsl.fetchExists(selectOne().from(STORAGE_UPLOAD_TICKETS)
                    .where(STORAGE_UPLOAD_TICKETS.ID.eq(ticketId), orphan(Instant.now().minus(ORPHAN_GRACE))));
            if (!stillOrphan) return null;
            tickets.delete(ticket);
            return ticket.getObjectKey();
        });
        if (key == null) return false;

        // 티켓 삭제가 커밋된 뒤에만 파일 삭제
        try {
            files.delete(key);
            for (ImageVariant variant : ImageVariant.values()) files.delete(ImageKeys.variantKey(key, variant));
        } catch (IOException | RuntimeException error) {
            log.error("쓰이지 않는 업로드 {}의 파일을 지우지 못했습니다", key, error);
        }
        return true;
    }

    /**
     * 정리 대상 티켓 조건
     *
     * 검증을 마친 업로드는 업로드 시각, 실패 처리된 티켓은 실패 시각을 기준으로 유예 시간을 셈
     * 실패 처리된 티켓은 연결할 수 없지만, 파일 저장 뒤 트랜잭션이 실패해 파일만 남았을 수 있어 함께 정리
     * 발급만 된 티켓은 만료 후 {@link #failExpiredTickets}가 실패 처리한 뒤에 대상이 됨
     *
     * @param cutoff 이 시각 이전에 업로드·실패 처리된 티켓만 대상
     * @return 유예 시간이 지났고 사용처가 없으면 참인 조건
     */
    private static Condition orphan(Instant cutoff) {
        return STORAGE_UPLOAD_TICKETS.STATUS.eq(StorageUploadTicketStatus.VERIFIED.name())
                .and(STORAGE_UPLOAD_TICKETS.VERIFIEDAT.le(cutoff))
                .or(STORAGE_UPLOAD_TICKETS.STATUS.eq(StorageUploadTicketStatus.FAILED.name())
                        .and(STORAGE_UPLOAD_TICKETS.FAILEDAT.le(cutoff)))
                .and(not(referenced(STORAGE_UPLOAD_TICKETS.OBJECTKEY)));
    }

    /**
     * 사진 사용처 조건
     *
     * 공개 여부·삭제 여부와 관계없이 DB에 남아 있는 모든 사용처를 셈
     * 숨긴 상품, 임시글, 삭제 표시만 된 상품·글은 되살릴 수 있고 주문 내역은 계속 보여야 하기 때문임
     * <ul>
     *   <li>상품 사진, 게시글 발행 사진, 주문 상품의 대표 사진 스냅샷: 키 일치</li>
     *   <li>게시글 대표 사진: 키 일치, 발행 본문 HTML·발행 문서·초안: 키 포함</li>
     *   <li>공지/FAQ 본문 HTML: 키 포함</li>
     * </ul>
     * 사진 키를 저장하는 곳을 새로 만들면 여기에 추가해야 함, 빠뜨리면 쓰고 있는 사진이 지워짐
     *
     * @param key 업로드 사진 키 열
     * @return 사용처가 하나라도 있으면 참인 조건
     */
    private static Condition referenced(Field<String> key) {
        return exists(selectOne().from(PRODUCT_IMAGES).where(PRODUCT_IMAGES.OBJECTKEY.eq(key)))
                .or(exists(selectOne().from(CMS_POST_IMAGES).where(CMS_POST_IMAGES.OBJECTKEY.eq(key))))
                .or(exists(selectOne().from(ORDER_ITEMS).where(ORDER_ITEMS.THUMBNAILIMAGEURLSNAPSHOT.eq(key))))
                .or(exists(selectOne().from(ADMIN_UPDATES).where(ADMIN_UPDATES.COVEROBJECTKEY.eq(key)
                        .or(ADMIN_UPDATES.BODYHTML.contains(key))
                        .or(ADMIN_UPDATES.DOCUMENTJSON.cast(SQLDataType.CLOB).contains(key))
                        .or(ADMIN_UPDATES.DRAFT.cast(SQLDataType.CLOB).contains(key)))))
                .or(exists(selectOne().from(CMS_CONTENTS).where(CMS_CONTENTS.BODYHTML.contains(key))));
    }

    /**
     * 업로드할 수 있는 티켓과 바이트인지 확인
     *
     * @throws ApiException 발급자가 아닐 때(403), 만료·사용했거나 서명·형식·크기가 티켓과 다를 때(400)
     */
    private static void assertUploadable(StorageUploadTicketEntity ticket, byte[] data, String contentType, UUID userId) {
        if (!ticket.getIssuedByUserId().equals(userId)) throw ApiException.forbidden();
        if (!ticket.isUsable(Instant.now())) throw ApiException.badRequest("만료되었거나 사용한 업로드입니다.");
        // 실제 바이트가 티켓에 신고한 사진과 같은지 확인
        if (!hasImageSignature(data, contentType) || !contentType.equals(ticket.getExpectedContentType())
                || data.length != ticket.getExpectedSizeBytes() || data.length > MAX_BYTES) {
            throw ApiException.badRequest("사진 형식 또는 실제 크기가 다릅니다.");
        }
    }

    /**
     * 크기별 변환본 생성
     *
     * @return 크기별 WebP 바이트, 변환기가 없는 환경에서는 빈 맵
     * @throws ApiException 사진을 해석하지 못하거나 변환이 제한 시간을 넘겼을 때(400)
     */
    private Map<ImageVariant, byte[]> convert(byte[] data) {
        if (!converter.available()) return Map.of();
        try {
            return converter.convert(data);
        } catch (IOException failed) {
            throw ApiException.badRequest("사진을 변환하지 못했습니다. 다른 사진으로 다시 시도해주세요.");
        }
    }

    /**
     * 요청한 크기 해석
     *
     * @param size 주소의 {@code size} 값, {@code null}이면 대
     * @throws ApiException 정해 둔 크기가 아닐 때(404)
     */
    private static ImageVariant variantOf(String size) {
        if (size == null) return ImageVariant.LARGE;
        return ImageVariant.ofCode(size).orElseThrow(ApiException::notFound);
    }

    /**
     * 저장된 변환본 읽기
     *
     * @param key 업로드 사진 키
     * @return 변환본 바이트, 변환본이 없거나 읽지 못하면 빈 값
     */
    private Optional<byte[]> variantBytes(String key, ImageVariant variant) {
        String variantKey = ImageKeys.variantKey(key, variant);
        if (!files.exists(variantKey)) return Optional.empty();
        try {
            return Optional.of(files.read(variantKey));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * 사진 접근 권한 확인
     *
     * 1. 공개 사용처가 있으면 통과, 응답이 요청자와 무관하므로 캐시 허용
     * 2. 삭제되지 않은 운영자면 통과
     * 3. 본인 주문 사용처가 있으면 통과
     *
     * @param key 업로드 사진 키
     * @param userId 세션 사용자 ID, 비로그인이면 빈 값
     * @return 공개 사용처에 쓰인 사진이면 참, 운영자·본인 주문으로만 볼 수 있는 사진이면 거짓
     * @throws ApiException 접근할 수 없을 때(404)
     */
    private boolean authorize(String key, Optional<UUID> userId) {
        if (dsl.fetchExists(selectOne().where(publiclyUsed(key)))) return true;
        Optional<UserEntity> user = userId.flatMap(users::findByIdAndDeletedAtIsNull);
        if (user.map(UserEntity::isAdmin).orElse(false)) return false;
        if (!dsl.fetchExists(selectOne().where(orderedBy(key, user.map(UserEntity::getId))))) throw ApiException.notFound();
        return false;
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
