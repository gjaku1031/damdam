/** 주문 상태별 고객·운영자 표시 이름과 배지 색 */
export const orderStatus: Record<string, { label: string; tone: string; description: string }> = {
  PENDING_PAYMENT: {
    label: '입금 대기',
    tone: 'badge-warning',
    description: '안내 계좌로 입금해주시면 확인 후 제작을 시작합니다.',
  },
  PAID: { label: '입금 확인', tone: 'badge-info', description: '입금이 확인되어 제작을 준비합니다.' },
  PROCESSING: { label: '제작·배송 중', tone: 'badge-violet', description: '상품을 만들거나 보내는 중입니다.' },
  COMPLETED: { label: '배송 완료', tone: 'badge-success', description: '상품을 받으셨습니다.' },
  CANCELED: { label: '주문 취소', tone: '', description: '취소된 주문입니다.' },
  PARTIALLY_REFUNDED: { label: '부분 환불', tone: 'badge-danger', description: '일부 금액을 환불했습니다.' },
  FULLY_REFUNDED: { label: '환불 완료', tone: 'badge-danger', description: '결제 금액을 모두 환불했습니다.' },
}

export const orderStatusOrder = [
  'PENDING_PAYMENT',
  'PAID',
  'PROCESSING',
  'COMPLETED',
  'CANCELED',
  'PARTIALLY_REFUNDED',
  'FULLY_REFUNDED',
] as const

export function orderStatusLabel(status: string) {
  return orderStatus[status]?.label ?? status
}

/** 운영자가 바꿀 수 있는 다음 상태 (API OrderTransitions와 같음) */
export const orderTransitions: Record<string, string[]> = {
  PENDING_PAYMENT: ['PAID', 'CANCELED'],
  PAID: ['PROCESSING', 'CANCELED', 'PARTIALLY_REFUNDED', 'FULLY_REFUNDED'],
  PROCESSING: ['COMPLETED', 'PARTIALLY_REFUNDED', 'FULLY_REFUNDED'],
  COMPLETED: ['PARTIALLY_REFUNDED', 'FULLY_REFUNDED'],
  PARTIALLY_REFUNDED: ['FULLY_REFUNDED'],
  CANCELED: [],
  FULLY_REFUNDED: [],
}

/** 운영자 화면의 상태 변경 버튼 문구 */
export const orderActionLabels: Record<string, string> = {
  PAID: '입금 확인',
  PROCESSING: '제작·발송 시작',
  COMPLETED: '배송 완료 처리',
  CANCELED: '주문 취소',
  PARTIALLY_REFUNDED: '부분 환불 기록',
  FULLY_REFUNDED: '전액 환불 기록',
}
