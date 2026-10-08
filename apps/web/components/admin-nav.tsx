'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

const links = [
  { href: '/mypage', label: '대시보드' },
  { href: '/mypage/orders', label: '주문' },
  { href: '/mypage/members', label: '회원' },
  { href: '/mypage/settings', label: '설정' },
  { href: '/mypage/history', label: '운영 기록' },
]

/** 운영 화면 사이를 오가는 탭 */
export function AdminNav() {
  const pathname = usePathname()
  return (
    <nav aria-label="운영 메뉴" className="-mx-1 mb-8 overflow-x-auto px-1">
      <div className="inline-flex gap-1 rounded-xl border border-border bg-card p-1">
        {links.map((link) => {
          const current = pathname === link.href
          return (
            <Link
              key={link.href}
              href={link.href}
              aria-current={current ? 'page' : undefined}
              className={`inline-flex min-h-10 items-center whitespace-nowrap rounded-lg px-4 text-sm font-semibold transition-colors ${
                current
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground'
              }`}
            >
              {link.label}
            </Link>
          )
        })}
      </div>
    </nav>
  )
}

/** 운영 화면 공통 머리말 */
export function AdminHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: React.ReactNode
}) {
  return (
    <>
      <div className="page-header mb-6">
        <div>
          <p className="eyebrow mb-2">운영 관리</p>
          <h1 className="page-title">{title}</h1>
          {description && <p className="page-description">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
      <AdminNav />
    </>
  )
}

/** 서울 기준 날짜·시각 표시 */
export function formatDateTime(value: string, withTime = true) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', hour12: false } : {}),
  }).format(new Date(value))
}
