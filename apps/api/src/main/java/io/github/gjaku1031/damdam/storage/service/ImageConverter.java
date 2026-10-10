package io.github.gjaku1031.damdam.storage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.gjaku1031.damdam.storage.domain.ImageVariant;

/**
 * 원본 사진을 크기별 WebP 변환본으로 바꾸는 변환기
 *
 * 변환은 별도 프로세스인 libvips({@code vips thumbnail})가 수행
 * <ul>
 *   <li>EXIF 회전 정보를 반영해 바로 세우고, 촬영 위치를 포함한 메타데이터는 모두 제거</li>
 *   <li>색 공간은 sRGB로 맞추고 투명 배경은 유지, 움직이는 사진은 첫 장만 사용</li>
 *   <li>원본보다 크게 키우지 않음</li>
 *   <li>사진을 푸는 메모리가 겹치지 않도록 한 번에 한 장만 변환</li>
 * </ul>
 *
 * {@code vips}가 설치되지 않은 환경(컨테이너 밖 개발 장비)에서는 변환 없이 원본만 쓰도록 {@link #available()}이 거짓
 */
@Component
final class ImageConverter {
    /**
     * 변환 로그
     */
    private static final Logger log = LoggerFactory.getLogger(ImageConverter.class);

    /**
     * 변환 프로그램 이름, 실행 경로({@code PATH})에서 찾음
     */
    private static final String COMMAND = "vips";

    /**
     * WebP 화질(0~100)
     */
    private static final int QUALITY = 80;

    /**
     * 변환본 한 벌의 제한 시간(초), 압축을 풀면 지나치게 커지는 사진을 중단
     */
    private static final long TIMEOUT_SECONDS = 20;

    /**
     * 세로 제한을 없애 가로만 기준으로 줄이기 위한 큰 값
     */
    private static final String UNBOUNDED_HEIGHT = "10000000";

    /**
     * 동시 변환을 한 장으로 제한하는 대기열, 요청 순서대로 진행
     */
    private final Semaphore slot = new Semaphore(1, true);

    /**
     * 변환 프로그램 사용 가능 여부
     */
    private final boolean available;

    /**
     * 시작할 때 변환 프로그램이 실행되는지 한 번 확인
     */
    ImageConverter() {
        this.available = probe();
        if (!available) {
            log.warn("Image converter '{}' is not installed; photos are stored and served without resized variants.", COMMAND);
        }
    }

    /**
     * 변환 프로그램 사용 가능 여부
     *
     * @return 사용할 수 없으면 거짓, 이때 {@link #convert(byte[])}는 호출하지 않음
     */
    boolean available() {
        return available;
    }

    /**
     * 원본 사진으로 모든 크기의 변환본 생성
     *
     * 임시 디렉터리에 원본을 쓰고 크기마다 변환한 뒤 임시 파일을 모두 삭제
     * 다른 변환이 진행 중이면 끝날 때까지 대기
     *
     * @param original JPG·PNG·WebP 원본 바이트
     * @return 크기별 WebP 바이트
     * @throws IOException 사진을 해석하지 못하거나 제한 시간을 넘겼거나 대기 중 중단됐을 때
     */
    Map<ImageVariant, byte[]> convert(byte[] original) throws IOException {
        try {
            slot.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Image conversion interrupted", interrupted);
        }
        Path workspace = Files.createTempDirectory("damdam-image-");
        try {
            // 형식은 확장자가 아니라 내용으로 판별되므로 원본 파일명은 고정
            Path source = Files.write(workspace.resolve("source"), original);
            Map<ImageVariant, byte[]> variants = new EnumMap<>(ImageVariant.class);
            for (ImageVariant variant : ImageVariant.values()) {
                Path target = workspace.resolve(variant.code() + ".webp");
                run(workspace, source, target, variant.width());
                variants.put(variant, Files.readAllBytes(target));
            }
            return variants;
        } finally {
            slot.release();
            deleteRecursively(workspace);
        }
    }

    /**
     * 변환본 한 벌 생성
     *
     * 프로그램 출력은 파이프가 막히지 않도록 파일로 받고, 실패했을 때만 끝부분을 로그에 남김
     *
     * @param width 최대 가로(px)
     * @throws IOException 종료 코드가 0이 아니거나 제한 시간을 넘겼을 때
     */
    private static void run(Path workspace, Path source, Path target, int width) throws IOException {
        Path output = workspace.resolve("output.log");
        Process process = new ProcessBuilder(List.of(COMMAND, "thumbnail", source.toString(),
                target + "[Q=" + QUALITY + ",keep=none]", String.valueOf(width),
                "--height", UNBOUNDED_HEIGHT, "--size", "down", "--export-profile", "srgb", "--fail-on", "error"))
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("Image conversion timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Image conversion interrupted", interrupted);
        } finally {
            // 제한 시간 초과·중단 시 남은 프로세스 종료
            process.destroyForcibly();
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(target)) {
            String message = Files.readString(output).strip();
            log.warn("Image conversion failed (width {}): {}", width, message.substring(Math.max(0, message.length() - 300)));
            throw new IOException("Image conversion failed");
        }
    }

    /**
     * 변환 프로그램 실행 가능 여부 확인
     *
     * @return {@code vips --version}이 5초 안에 정상 종료하면 참
     */
    private static boolean probe() {
        try {
            Process process = new ProcessBuilder(COMMAND, "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            process.destroyForcibly();
            return finished && process.exitValue() == 0;
        } catch (IOException missing) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 임시 디렉터리와 그 안의 파일 삭제, 실패는 로그만 남김
     */
    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("Failed to remove temporary image directory {}", directory, error);
        }
    }
}
