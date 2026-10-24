package io.github.gjaku1031.damdam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

import io.github.gjaku1031.damdam.storage.dto.ImageBackfillResult;
import io.github.gjaku1031.damdam.storage.service.ImageBackfillService;

/**
 * 담담공방 API 진입점
 *
 * <ul>
 *   <li>인자 없음: HTTP API 서버 (시작 시 운영자·약관·기본 설정 준비)</li>
 *   <li>{@code seed}: 웹 서버 없이 기본 데이터만 준비하고 종료</li>
 *   <li>{@code optimize-images}: 변환본이 없는 사진에 크기별 변환본을 만들고 종료</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DamdamApiApplication {
    /**
     * 첫 인자에 따라 서버 실행 또는 일회성 명령 실행
     *
     * @param args 명령행 인자, 첫 값이 {@code seed}·{@code optimize-images}면 해당 명령 실행
     */
    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        switch (command) {
            case "seed" -> System.exit(SpringApplication.exit(commandLine(args)));
            case "optimize-images" -> runImageBackfill(args);
            default -> SpringApplication.run(DamdamApiApplication.class, args);
        }
    }

    /**
     * 일회성 명령용 컨텍스트 시작
     *
     * 웹 서버와 정리 스케줄러를 끈 상태로 실행, 시작 시 {@code SeedService}가 기본 데이터 준비
     *
     * @param args 명령행 인자
     * @return 시작된 컨텍스트, 닫을 책임은 호출자에게 있음
     */
    private static ConfigurableApplicationContext commandLine(String[] args) {
        return new SpringApplicationBuilder(DamdamApiApplication.class)
                .web(WebApplicationType.NONE)
                .properties("damdam.scheduling.enabled=false")
                .run(args);
    }

    /**
     * 변환본이 없는 사진의 변환본 일괄 생성 후 프로세스 종료
     *
     * 여러 번 실행해도 이미 변환한 사진은 건너뜀
     * 실패한 사진이 있거나 변환기가 없으면 종료 코드 1, 모두 처리하면 0
     *
     * @param args 명령행 인자
     */
    private static void runImageBackfill(String[] args) {
        // 컨텍스트를 닫은 뒤 종료 코드 반환
        int status = 0;
        try (ConfigurableApplicationContext context = commandLine(args)) {
            ImageBackfillResult result = context.getBean(ImageBackfillService.class).run();
            System.out.println("Image variants: converted=" + result.converted() + ", skipped=" + result.skipped()
                    + ", failed=" + result.failed());
            if (result.failed() > 0) status = 1;
        } catch (RuntimeException error) {
            System.err.println(error.getMessage());
            status = 1;
        }
        System.exit(status);
    }
}
