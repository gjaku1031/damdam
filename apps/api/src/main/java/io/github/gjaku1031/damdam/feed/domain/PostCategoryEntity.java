package io.github.gjaku1031.damdam.feed.domain;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 분류
 */
@Getter
@Entity
@Table(name = "post_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCategoryEntity extends BaseEntity {
    /**
     * 분류명, 유일
     */
    @Column(nullable = false, unique = true, length = 40)
    private String name;

    /**
     * 분류 생성
     *
     * @param name 앞뒤 공백을 제거한 이름
     * @return 저장 전 분류
     */
    public static PostCategoryEntity named(String name) {
        PostCategoryEntity category = new PostCategoryEntity();
        category.name = name;
        return category;
    }

    /**
     * 이름 변경
     */
    public void rename(String name) {
        this.name = name;
    }
}
