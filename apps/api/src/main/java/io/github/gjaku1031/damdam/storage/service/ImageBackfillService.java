package io.github.gjaku1031.damdam.storage.service;

import static io.github.gjaku1031.damdam.jooq.Tables.STORAGE_UPLOAD_TICKETS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.gjaku1031.damdam.config.DamdamProperties;
import io.github.gjaku1031.damdam.storage.domain.ImageVariant;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketStatus;
import io.github.gjaku1031.damdam.storage.dto.ImageBackfillResult;
import lombok.RequiredArgsConstructor;

/**
 * 이미 저장된 사진의 변환본 일괄 생성({@code optimize-images} 명령 전용)
 *
 * 변환본 도입 전에 올린 사진과 기존 정적 사진({@code /images/...})에 크기별 변환본을 만들어 둠
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
     * 기존 정적 사진 경로 설정
     */
    private final DamdamProperties properties;

    /**
     * 변환본이 없는 모든 사진에 변환본 생성
     *
     * @return 새로 만든·건너뛴·실패한 사진 수
     * @throws IllegalStateException 변환기({@code vips})가 설치되지 않았을 때
     * @throws UncheckedIOException 기존 정적 사진 디렉터리를 읽지 못할 때
     */
    public ImageBackfillResult run() {
        if (!converter.available()) throw new IllegalStateException("Image converter (vips) is not installed.");
        // 업로드 사진은 검증 완료 티켓의 키, 기존 정적 사진은 디렉터리의 파일 목록이 대상
        List<String> keys = new ArrayList<>(dsl.select(STORAGE_UPLOAD_TICKETS.OBJECTKEY).from(STORAGE_UPLOAD_TICKETS)
                .where(STORAGE_UPLOAD_TICKETS.STATUS.eq(StorageUploadTicketStatus.VERIFIED.name()))
                .orderBy(STORAGE_UPLOAD_TICKETS.OBJECTKEY)
                .fetch(STORAGE_UPLOAD_TICKETS.OBJECTKEY));
        keys.addAll(legacyKeys());
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
                for (Map.Entry<ImageVariant, byte[]> variant : converter.convert(original(key)).entrySet()) {
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

    /**
     * 기존 정적 사진 디렉터리의 사진 키 목록
     *
     * @return {@code /images/...} 형식의 키(이름순), 디렉터리가 없으면 빈 목록
     * @throws UncheckedIOException 디렉터리를 읽지 못할 때
     */
    private List<String> legacyKeys() {
        Path root = legacyRoot();
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(filename -> ImageKeys.LEGACY_FILENAME.matcher(filename).matches())
                    .sorted()
                    .map(filename -> ImageKeys.LEGACY_PREFIX + filename)
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /**
     * 원본 사진 바이트 읽기
     *
     * @param key 업로드 사진 키 또는 {@code /images/...} 경로
     * @throws IOException 파일이 없거나 읽지 못할 때
     */
    private byte[] original(String key) throws IOException {
        return key.startsWith(ImageKeys.LEGACY_PREFIX)
                ? Files.readAllBytes(legacyRoot().resolve(key.substring(ImageKeys.LEGACY_PREFIX.length())))
                : files.read(key);
    }

    /**
     * 기존 정적 사진 디렉터리의 정규화한 절대 경로
     */
    private Path legacyRoot() {
        return Path.of(properties.storage().legacyImagesPath()).toAbsolutePath().normalize();
    }
}
