/** 장바구니에 담긴 옵션 하나 */
export interface CartItem {
  productId: string
  variantId: string
  quantity: number
}

/** 원화 금액 표시 (예: 48,000원) */
export function formatPrice(price: number): string {
  return new Intl.NumberFormat('ko-KR').format(price) + '원'
}
