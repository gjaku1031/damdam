package io.github.gjaku1031.damdam.seed;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.auth.dto.AuthRules;
import io.github.gjaku1031.damdam.auth.service.PasswordHasher;
import io.github.gjaku1031.damdam.config.DamdamProperties;
import io.github.gjaku1031.damdam.system.domain.SystemConfigEntity;
import io.github.gjaku1031.damdam.system.repository.SystemConfigRepository;
import io.github.gjaku1031.damdam.user.domain.TermsEntity;
import io.github.gjaku1031.damdam.user.domain.TermsType;
import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.domain.UserRole;
import io.github.gjaku1031.damdam.user.repository.TermsRepository;
import io.github.gjaku1031.damdam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 시작할 때마다 운영에 꼭 필요한 기본 데이터 준비
 *
 * 상품·고객·주문 목데이터는 만들지 않음
 *
 * <ul>
 *   <li>초기 운영자: {@code SEED_ADMIN_PASSWORD}가 있고 운영자가 없거나 운영자의 해시가 Argon2id가 아닐 때만 생성·갱신</li>
 *   <li>무통장 입금 안내·사업자 정보·고객센터 설정(빈 값), 현재 필수 약관</li>
 * </ul>
 */
@Service
@Order(0)
@RequiredArgsConstructor
public class SeedService implements ApplicationRunner {
    /**
     * 초기 운영자 아이디 형식, 가입 규칙과 같음
     */
    private static final Pattern USERNAME = Pattern.compile(AuthRules.USERNAME);

    /**
     * 초기 운영자 비밀번호 형식, 가입 규칙과 같음
     */
    private static final Pattern PASSWORD = Pattern.compile(AuthRules.PASSWORD);

    /**
     * 빈 값으로 준비할 설정 키와 설명, 입력 순서 유지
     */
    private static final Map<String, String> DEFAULT_CONFIGS = defaultConfigs();

    /**
     * 회원 저장소
     */
    private final UserRepository users;

    /**
     * 약관 저장소
     */
    private final TermsRepository terms;

    /**
     * 운영 설정 저장소
     */
    private final SystemConfigRepository configs;

    /**
     * 비밀번호 해시 도구
     */
    private final PasswordHasher passwords;

    /**
     * 초기 운영자 계정 설정
     */
    private final DamdamProperties properties;

    /**
     * 애플리케이션 시작 시 기본 데이터 준비
     *
     * @param args 실행 인자, 사용하지 않음
     */
    @Override
    public void run(ApplicationArguments args) {
        seedDefaults();
    }

    /**
     * 없는 기본 데이터만 추가
     *
     * 하나의 트랜잭션에서 실행, 실패 시 운영자 계정·설정·약관 변경이 함께 롤백됨
     *
     * 1. 초기 운영자 생성·갱신
     * 2. 무통장 입금 안내·사업자 정보·고객센터 설정(빈 값) 추가
     * 3. 현재 버전의 서비스·개인정보 약관 추가
     *
     * @throws IllegalStateException 초기 운영자 아이디·비밀번호 형식이 잘못됐을 때(시작 중단)
     */
    @Transactional
    public void seedDefaults() {
        bootstrapAdministrator();
        // 이미 있는 설정·약관은 운영자가 바꾼 값을 유지
        DEFAULT_CONFIGS.forEach((key, description) -> {
            if (!configs.existsById(key)) configs.save(SystemConfigEntity.of(key, "", description, null));
        });
        for (TermsType type : new TermsType[] {TermsType.SERVICE, TermsType.PRIVACY}) {
            if (terms.findByTypeAndVersion(type, TermsEntity.CURRENT_VERSION).isEmpty()) {
                terms.save(TermsEntity.current(type));
            }
        }
    }

    /**
     * 초기 운영자 계정 생성 또는 자격 증명 갱신
     *
     * 1. 설정된 비밀번호가 없으면 아무것도 하지 않음
     * 2. 현재 형식 해시를 가진 운영자가 이미 있으면 유지
     * 3. 아이디·비밀번호 형식 검사 후 해시 생성
     * 4. 기존 운영자가 있으면 자격 증명 교체, 없으면 새 운영자 저장
     *
     * @throws IllegalStateException 아이디·비밀번호가 가입 규칙에 맞지 않을 때
     */
    private void bootstrapAdministrator() {
        // 비밀번호를 설정하지 않은 환경은 운영자 준비 생략
        String password = properties.seed().adminPassword();
        if (password == null || password.isEmpty()) return;
        String username = Optional.ofNullable(properties.seed().adminUsername()).orElse("admin").trim()
                .toLowerCase(Locale.ROOT);
        // 이미 현재 해시 형식인 운영자는 설정값으로 덮어쓰지 않음
        Optional<UserEntity> existing = users.findFirstByRoleAndDeletedAtIsNull(UserRole.ADMIN);
        if (existing.isPresent() && passwords.isCurrentFormat(existing.get().getPasswordHash())) return;
        // 형식 검사 후 계정 생성·갱신
        if (!USERNAME.matcher(username).matches() || !PASSWORD.matcher(password).matches()) {
            throw new IllegalStateException("Invalid bootstrap administrator credentials.");
        }
        String hash = passwords.hash(password);
        existing.ifPresentOrElse(admin -> admin.changeCredentials(username, hash),
                () -> users.save(UserEntity.create(username, hash, UserRole.ADMIN)));
    }

    /**
     * 기본 설정 키·설명 목록 생성
     *
     * @return 입력 순서를 유지한 설정 키별 설명
     */
    private static Map<String, String> defaultConfigs() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(SystemConfigEntity.BANK_TRANSFER_ACCOUNT, "무통장 입금 계좌 안내");
        defaults.put(SystemConfigEntity.BANK_TRANSFER_DUE_DAYS, "무통장 입금 기한(일)");
        defaults.put(SystemConfigEntity.BUSINESS_NAME, "상호");
        defaults.put(SystemConfigEntity.BUSINESS_OWNER, "대표자");
        defaults.put(SystemConfigEntity.BUSINESS_NUMBER, "사업자등록번호");
        defaults.put(SystemConfigEntity.MAIL_ORDER_NUMBER, "통신판매업 신고번호");
        defaults.put(SystemConfigEntity.BUSINESS_ADDRESS, "사업장 주소");
        defaults.put(SystemConfigEntity.CS_PHONE, "고객센터 전화번호");
        defaults.put(SystemConfigEntity.CS_HOURS, "고객센터 운영 시간");
        defaults.put(SystemConfigEntity.CS_EMAIL, "고객센터 이메일");
        return defaults;
    }
}
