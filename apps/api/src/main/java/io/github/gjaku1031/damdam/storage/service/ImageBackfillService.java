package io.github.gjaku1031.damdam.storage.service;

import static io.github.gjaku1031.damdam.jooq.Tables.STORAGE_UPLOAD_TICKETS;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.gjaku1031.damdam.storage.domain.ImageVariant;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketStatus;
import io.github.gjaku1031.damdam.storage.dto.ImageBackfillResult;
import lombok.RequiredArgsConstructor;

/**
 * 이미 저장된 사진의 변환본 일괄 생성({@code optimize-images} 명령 전용)
 *
 * 변환기({@code vips})가 없는 환경에서 올려 원본만 저장된 업로드 사진에 크기별 변환본을 만들어 둠
 * 변환본이 모두 있는 사진은 건너뛰므로 여러 번 실행해도 결과가 같음
 * 한 장이 실패해도 나머지를 계속 처리하고, 실패한 사진은 원본으로 계속 제공됨
 */
@Service
@RequiredArgsConstructor
public final class ImageBackfillService {
    /**
     * 일괄 변환 로그
     */
    private static final Logger log = LoggerFactory.getLogger(ImageBackfillService.class);

    /**
     * 검증 완료된 업로드 사진 키 조회용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 업로드 원본·변환본 저장소
     */
    private final FileStorage files;

    /**
     * 크기별 변환본 생성기
     */
    private final ImageConverter converter;

    /**
     * 변환본이 없는 모든 사진에 변환본 생성
     *
     * @return 새로 만든·건너뛴·실패한 사진 수
     * @throws IllegalStateException 변환기({@code vips})가 설치되지 않았을 때
     */
    public ImageBackfillResult run() {
        if (!converter.available()) throw new IllegalStateException("Image converter (vips) is not installed.");
        // 검증을 마친 업로드 티켓의 키가 대상
        List<String> keys = dsl.select(STORAGE_UPLOAD_TICKETS.OBJECTKEY).from(STORAGE_UPLOAD_TICKETS)
                .where(STORAGE_UPLOAD_TICKETS.STATUS.eq(StorageUploadTicketStatus.VERIFIED.name()))
                .orderBy(STORAGE_UPLOAD_TICKETS.OBJECTKEY)
                .fetch(STORAGE_UPLOAD_TICKETS.OBJECTKEY);
        int converted = 0;
        int skipped = 0;
        int failed = 0;
        for (String key : keys) {
            // 변환본이 모두 있으면 건너뜀
            if (Arrays.stream(ImageVariant.values()).allMatch(variant -> files.exists(ImageKeys.variantKey(key, variant)))) {
                skipped++;
                continue;
            }
            try {
                for (Map.Entry<ImageVariant, byte[]> variant : converter.convert(files.read(key)).entrySet()) {
                    files.save(ImageKeys.variantKey(key, variant.getKey()), variant.getValue());
                }
                converted++;
            } catch (IOException error) {
                log.warn("Skipped image {}: {}", key, error.toString());
                failed++;
            }
        }
        return new ImageBackfillResult(converted, skipped, failed);
    }
}
