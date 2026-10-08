package io.github.gjaku1031.damdam.cms.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항·FAQ
 *
 * 본문 HTML은 저장 전에 정제된 값
 */
@Getter
@Entity
@Table(name = "cms_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CmsContentEntity extends SoftDeletableEntity {
    /**
     * 공지·FAQ 구분
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CmsContentType type;

    /**
     * 제목
     */
    @Column(nullable = false)
    private String title;

    /**
     * 정제된 본문 HTML
     */
    @Column(nullable = false, columnDefinition = "text")
    private String bodyHtml;

    /**
     * 고객 화면 공개 여부
     */
    @Column(nullable = false)
    private boolean visible = true;

    /**
     * 작성 운영자 ID
     */
    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    /**
     * 공개 상태의 글 생성
     *
     * @param bodyHtml 정제된 본문
     * @return 저장 전 글
     */
    public static CmsContentEntity create(CmsContentType type, String title, String bodyHtml, UUID authorUserId) {
        CmsContentEntity content = new CmsContentEntity();
        content.type = type;
        content.title = title;
        content.bodyHtml = bodyHtml;
        content.authorUserId = authorUserId;
        return content;
    }

    /**
     * 공개
     */
    public void show() {
        visible = true;
    }

    /**
     * 숨김
     */
    public void hide() {
        visible = false;
    }

    /**
     * 제목·본문·공개 여부 일괄 변경
     *
     * @param bodyHtml 정제된 본문
     */
    public void updateContent(String title, String bodyHtml, boolean visible) {
        this.title = title;
        this.bodyHtml = bodyHtml;
        this.visible = visible;
    }
}
