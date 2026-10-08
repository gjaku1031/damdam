package io.github.gjaku1031.damdam;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

import io.github.gjaku1031.damdam.seed.DemoDataSeeder;

/**
 * 담담공방 API 진입점
 *
 * <ul>
 *   <li>인자 없음: HTTP API 서버 (시작 시 운영자·약관·기본 설정 준비)</li>
 *   <li>{@code seed}: 웹 서버 없이 기본 데이터만 준비하고 종료</li>
 *   <li>{@code seed-demo --apply}: 체험 주문·사진을 추가하고 종료 ({@code DEMO_CUSTOMER_PASSWORD} 필요)</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DamdamApiApplication {
    /**
     * 첫 인자에 따라 서버 실행 또는 일회성 명령 실행
     *
     * @param args 명령행 인자, 첫 값이 {@code seed}·{@code seed-demo}면 해당 명령 실행
     */
    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        switch (command) {
            case "seed" -> System.exit(SpringApplication.exit(commandLine(args)));
            case "seed-demo" -> runDemoSeed(args);
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
     * 체험 주문·사진 추가 후 프로세스 종료
     *
     * 1. {@code --apply} 인자가 없으면 안내 후 종료 코드 1로 종료(백업 없는 실수 실행 방지)
     * 2. 일회성 컨텍스트에서 {@link DemoDataSeeder} 실행, 결과 요약 출력
     * 3. 실패 시 메시지 출력 후 종료 코드 1, 성공 시 0
     *
     * @param args 명령행 인자
     */
    private static void runDemoSeed(String[] args) {
        // 명시적 확인 인자 없이 데이터를 추가하지 않음
        if (!Arrays.asList(args).contains("--apply")) {
            System.err.println("This command adds demo orders and photos. Pass --apply explicitly after taking a backup.");
            System.exit(1);
        }
        // 컨텍스트를 닫은 뒤 종료 코드 반환
        int status = 0;
        try (ConfigurableApplicationContext context = commandLine(args)) {
            String password = System.getenv().getOrDefault("DEMO_CUSTOMER_PASSWORD", "");
            System.out.println(context.getBean(DemoDataSeeder.class).seed(password));
        } catch (RuntimeException error) {
            System.err.println(error.getMessage());
            status = 1;
        }
        System.exit(status);
    }
}
