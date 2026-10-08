package io.github.gjaku1031.damdam.feed.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * 초안 저장 요청
 *
 * @param revision 편집을 시작한 리비전, 현재 값과 다르면 409
 * @param title 제목, 최대 200자
 * @param documentJson TipTap 문서 객체
 * @param categoryId 분류 ID, 없으면 분류 해제
 * @param tags 태그, 최대 10개·각 30자
 * @param coverObjectKey 대표 사진 키, 본문 사진 중 하나여야 하며 없으면 첫 사진
 */
public record DraftRequest(
        @NotNull @Min(0) Integer revision,
        @NotNull @Size(max = 200) String title,
        @NotNull JsonNode documentJson,
        UUID categoryId,
        @NotNull @Size(max = 10) List<@NotNull @Size(max = 30) String> tags,
        @Size(max = 200) String coverObjectKey) {
    /**
     * 본문이 JSON 객체인지 검증
     *
     * @return 객체이거나 값이 없어 다른 검증에 맡길 때 {@code true}
     */
    @AssertTrue(message = "documentJson must be an object")
    public boolean isDocumentObject() {
        return documentJson == null || documentJson.isObject();
    }
}
