package io.github.gjaku1031.damdam.storage.service;

import java.io.IOException;

/**
 * 업로드 파일 저장소 계약
 *
 * 현재는 로컬 디스크를 쓰지만 S3 등으로 바꿔도 서비스 계층은 이 추상화만 의존
 */
interface FileStorage {
    /**
     * 파일 저장
     *
     * @param objectKey 저장 키
     * @param data 파일 바이트
     * @throws IOException 쓰기 실패
     */
    void save(String objectKey, byte[] data) throws IOException;

    /**
     * 파일 읽기
     *
     * @param objectKey 저장 키
     * @return 파일 바이트
     * @throws IOException 파일이 없거나 읽기 실패
     */
    byte[] read(String objectKey) throws IOException;

    /**
     * 파일 삭제
     *
     * @param objectKey 저장 키
     * @throws IOException 삭제 실패
     */
    void delete(String objectKey) throws IOException;

    /**
     * 파일 존재 여부 확인
     *
     * @param objectKey 저장 키
     * @return 존재 여부
     */
    boolean exists(String objectKey);
}
