package io.github.gjaku1031.damdam.storage.dto;

import io.github.gjaku1031.damdam.storage.domain.UploadDomain;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 업로드 티켓 발급 요청
 *
 * @param domain 사진을 쓸 도메인, 필수
 * @param contentType MIME 형식({@code image/jpeg|image/png|image/webp}), 필수
 * @param sizeBytes 파일 크기(1바이트~5MB), 필수
 */
public record IssueTicketRequest(
        @NotNull UploadDomain domain,
        @NotNull @Pattern(regexp = "image/jpeg|image/png|image/webp", message = "JPG·PNG·WebP 사진만 올릴 수 있습니다.")
        String contentType,
        @NotNull @Min(1) @Max(5 * 1024 * 1024) Integer sizeBytes) {}
