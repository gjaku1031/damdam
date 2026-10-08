package io.github.gjaku1031.damdam.storage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import io.github.gjaku1031.damdam.config.DamdamProperties;

/**
 * 로컬 디스크 파일 저장소
 *
 * 저장 키를 {@code STORAGE_LOCAL_PATH} 하위 경로로 변환해 저장
 */
@Component
final class LocalFileStorage implements FileStorage {
    /**
     * 정규화한 절대 기준 경로
     */
    private final Path basePath;

    /**
     * 설정의 로컬 저장 경로를 절대 경로로 정규화해 기준 경로로 사용
     */
    LocalFileStorage(DamdamProperties properties) {
        this.basePath = Path.of(properties.storage().localPath()).toAbsolutePath().normalize();
    }

    /**
     * 상위 디렉터리를 만든 뒤 파일 저장, 같은 키가 있으면 덮어씀
     */
    @Override
    public void save(String objectKey, byte[] data) throws IOException {
        Path file = resolve(objectKey);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
    }

    /**
     * 파일 전체 읽기
     */
    @Override
    public byte[] read(String objectKey) throws IOException {
        return Files.readAllBytes(resolve(objectKey));
    }

    /**
     * 파일 삭제, 없으면 무시
     */
    @Override
    public void delete(String objectKey) throws IOException {
        // 이미 지워진 파일은 정리 작업에서 흔하므로 무시
        Files.deleteIfExists(resolve(objectKey));
    }

    /**
     * 일반 파일 존재 여부 확인
     */
    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    /**
     * 저장 키를 기준 경로 하위의 파일 경로로 변환
     *
     * @return 정규화한 파일 경로
     * @throws IllegalArgumentException 기준 경로 밖({@code ../} 등)이거나 기준 경로 자체일 때
     */
    private Path resolve(String objectKey) {
        Path resolved = basePath.resolve(objectKey).normalize();
        if (!resolved.startsWith(basePath) || resolved.equals(basePath)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return resolved;
    }
}
