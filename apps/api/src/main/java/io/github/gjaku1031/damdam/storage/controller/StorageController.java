package io.github.gjaku1031.damdam.storage.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.gjaku1031.damdam.common.auth.AdminOnly;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.auth.SessionUser;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.storage.dto.IssueTicketRequest;
import io.github.gjaku1031.damdam.storage.dto.StoredImage;
import io.github.gjaku1031.damdam.storage.dto.UploadTicketResponse;
import io.github.gjaku1031.damdam.storage.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 사진 업로드·제공 API
 *
 * 업로드는 운영자 전용, 사진 제공은 {@link StorageService}의 접근 권한 판단을 따름
 * 사진 주소의 {@code size}({@code s}·{@code m}·{@code l})로 크기별 변환본을 고르고, 생략하면 대를 보냄
 */
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public final class StorageController {
    /**
     * 공개 사진의 브라우저·CDN 캐시 기간, 숨긴 사진이 캐시에 남는 시간의 상한
     */
    private static final Duration PUBLIC_CACHE = Duration.ofDays(1);

    /**
     * 사진 저장 서비스
     */
    private final StorageService storage;

    /**
     * 업로드 티켓 발급
     *
     * @return 저장 키와 만료 시각을 담은 티켓
     */
    @AdminOnly
    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicketResponse issue(@Valid @RequestBody IssueTicketRequest body, CurrentUser user) {
        return storage.issueTicket(user.id(), body.domain(), body.contentType(), body.sizeBytes());
    }

    /**
     * 티켓으로 multipart {@code file} 업로드
     *
     * @param file 업로드 파일, 없거나 비어 있으면 400
     * @return {@code {objectKey}}
     * @throws ApiException 파일이 없거나 비었을 때(400)
     * @throws UncheckedIOException 요청 파일 읽기 실패
     */
    @AdminOnly
    @PostMapping("/upload/{ticketId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@PathVariable UUID ticketId,
            @RequestParam(value = "file", required = false) MultipartFile file, CurrentUser user) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("사진을 선택해주세요.");
        try {
            return storage.upload(ticketId, file.getBytes(), String.valueOf(file.getContentType()), user.id());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /**
     * 업로드 사진 제공
     *
     * @param domain 저장 키의 도메인 접두사
     * @param filename 저장 키의 파일명
     * @param size 변환본 크기, 생략하면 대
     * @return 공개 사진은 캐시 허용, 그 외에는 저장 금지 정책의 사진 응답
     */
    @GetMapping("/files/{domain}/{filename}")
    public ResponseEntity<byte[]> file(@PathVariable String domain, @PathVariable String filename,
            @RequestParam(value = "size", required = false) String size, HttpServletRequest request) {
        return image(storage.file(domain + "/" + filename, SessionUser.id(request), size));
    }

    /**
     * 사진 응답 조립
     *
     * 공개 사용처에 쓰인 사진만 하루 동안 공용 캐시를 허용하고, 운영자·본인 주문으로만 볼 수 있는 사진은 저장 금지
     * 사진 키는 올릴 때마다 새로 만들어져 같은 주소의 내용이 바뀌지 않으므로 캐시된 응답이 다른 사진이 되는 일은 없음
     *
     * @return MIME 추측 금지 헤더를 붙인 200 사진 응답
     */
    private static ResponseEntity<byte[]> image(StoredImage image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(image.cacheable() ? CacheControl.maxAge(PUBLIC_CACHE).cachePublic() : CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
    }
}
