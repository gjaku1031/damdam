package io.github.gjaku1031.damdam.order.domain;

/**
 * 주문자 연락처와 배송지
 *
 * @param buyerName 주문자 이름
 * @param buyerPhone 주문자 연락처
 * @param shipName 받는 사람
 * @param shipPhone 받는 사람 연락처
 * @param shipPostalCode 우편번호
 * @param shipAddress1 기본 주소
 * @param shipAddress2 상세 주소, 없으면 빈 문자열
 * @param shipMemo 배송 메모, 없으면 빈 문자열
 */
public record OrderContact(String buyerName, String buyerPhone, String shipName, String shipPhone,
        String shipPostalCode, String shipAddress1, String shipAddress2, String shipMemo) {}
