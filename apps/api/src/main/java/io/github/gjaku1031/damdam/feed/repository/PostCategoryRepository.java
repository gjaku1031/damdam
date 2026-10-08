package io.github.gjaku1031.damdam.feed.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.feed.domain.PostCategoryEntity;

/**
 * 게시글 분류 저장소
 */
public interface PostCategoryRepository extends JpaRepository<PostCategoryEntity, UUID> {}
