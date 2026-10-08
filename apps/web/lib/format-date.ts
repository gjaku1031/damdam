/** 한국 시간 기준 날짜 (2026년 10월 8일) */
export function formatDate(value: string) {
  return new Date(value).toLocaleDateString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

/** 한국 시간 기준 날짜와 시각 (2026. 10. 8. 오후 2:20) */
export function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}
