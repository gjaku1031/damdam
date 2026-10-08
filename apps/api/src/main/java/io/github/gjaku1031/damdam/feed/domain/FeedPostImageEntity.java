package io.github.gjaku1031.damdam.feed.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발행된 게시글 본문의 사진 목록
 *
 * 발행할 때마다 전체 교체, 공개 사진 접근 권한 판단에 사용
 */
@Getter
@Entity
@Table(name = "cms_post_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedPostImageEntity extends BaseEntity {
    /**
     * 게시글 ID
     */
    @Column(name = "post_id", nullable = false)
    private UUID postId;

    /**
     * 사진 저장 키
     */
    @Column(nullable = false)
    private String objectKey;

    /**
     * 본문 등장 순서, 게시글 안에서 유일
     */
    @Column(nullable = false)
    private int sortOrder;

    /**
     * 사진 목록 항목 생성
     *
     * @return 저장 전 항목
     */
    public static FeedPostImageEntity of(UUID postId, String objectKey, int sortOrder) {
        FeedPostImageEntity image = new FeedPostImageEntity();
        image.postId = postId;
        image.objectKey = objectKey;
        image.sortOrder = sortOrder;
        return image;
    }
}
