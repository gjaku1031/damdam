/** 무통장 입금 기한 표시. 기한은 서버가 주문할 때 계산해 주문에 저장한 값(paymentDueAt)을 그대로 쓴다. */
const SEOUL = 'Asia/Seoul'
const DAY_MS = 86_400_000

const dueFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})
const dayFormat = new Intl.DateTimeFormat('en-CA', { timeZone: SEOUL })

/** 한국 시간 기준 날짜(YYYY-MM-DD) */
export function seoulDay(date: Date) {
  return dayFormat.format(date)
}

/** '10월 12일(월) 23:59' 형식 */
export function formatPaymentDue(iso: string) {
  const parts = Object.fromEntries(dueFormat.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
  return `${parts.month}월 ${parts.day}일(${parts.weekday}) ${parts.hour}:${parts.minute}`
}

/** 결제 대기 주문 옆에 붙이는 마감 배지. 기한이 없는 주문은 null */
export function paymentDueBadge(iso: string | null | undefined, now = new Date()) {
  if (!iso) return null
  const due = new Date(iso)
  if (due.getTime() <= now.getTime()) return { label: '기한 지남', tone: 'badge-danger' }
  const days = Math.round((Date.parse(seoulDay(due)) - Date.parse(seoulDay(now))) / DAY_MS)
  if (days === 0) return { label: '오늘 마감', tone: 'badge-danger' }
  return { label: `마감 ${days}일 전`, tone: days === 1 ? 'badge-warning' : '' }
}

/** 날짜 입력값(YYYY-MM-DD)을 그날 23:59:59(한국 시간) 기한으로 변환 */
export function paymentDueFromDay(day: string) {
  return new Date(day + 'T23:59:59+09:00').toISOString()
}

/** 날짜 입력에서 고를 수 있는 가장 늦은 날: 오늘부터 max일 뒤 */
export function maxPaymentDueDay(maxDays: number, now = new Date()) {
  return seoulDay(new Date(now.getTime() + (maxDays - 1) * DAY_MS))
}
