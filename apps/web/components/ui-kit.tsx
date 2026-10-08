import Link from 'next/link'
import type { ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { orderStatus } from '@/lib/order-status'

/** 나이테를 단순화한 로고 표시 + 글자 */
export function Logo({ inverted = false }: { inverted?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <svg
        width="28"
        height="28"
        viewBox="0 0 28 28"
        fill="none"
        stroke={inverted ? '#f1eae1' : 'var(--primary)'}
        strokeWidth="1.7"
        strokeLinecap="round"
        aria-hidden="true"
        className="shrink-0"
      >
        <circle cx="14" cy="14" r="12" />
        <path d="M14 6.5a7.5 7.5 0 1 1-7.5 7.5" />
        <path d="M14 10.5a3.5 3.5 0 1 1-3.5 3.5" />
      </svg>
      <span
        className={`text-[19px] font-bold tracking-[-0.02em] ${inverted ? 'text-white' : 'text-foreground'}`}
      >
        담담공방
      </span>
    </span>
  )
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  back,
}: {
  eyebrow?: ReactNode
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  back?: { href: string; label: string }
}) {
  return (
    <div className="page-header">
      <div className="min-w-0">
        {back && (
          <Link
            href={back.href}
            className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ChevronLeft className="size-4" aria-hidden="true" />
            {back.label}
          </Link>
        )}
        {eyebrow && <p className="eyebrow mb-2">{eyebrow}</p>}
        <h1 className="page-title">{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="card flex flex-col items-center gap-3 px-6 py-14 text-center">
      {icon && (
        <span className="mb-1 flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
          {icon}
        </span>
      )}
      <p className="text-base font-semibold">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const info = orderStatus[status]
  return <span className={`badge ${info?.tone ?? ''}`}>{info?.label ?? status}</span>
}

/** 쪽 이동: hrefFor를 주면 링크, onChange를 주면 버튼 */
export function Pagination({
  page,
  total,
  size,
  hrefFor,
  onChange,
  label = '페이지',
}: {
  page: number
  total: number
  size: number
  hrefFor?: (page: number) => string
  onChange?: (page: number) => void
  label?: string
}) {
  const last = Math.max(1, Math.ceil(total / size))
  if (last <= 1) return null
  const start = Math.max(1, Math.min(page - 2, last - 4)),
    pages = Array.from({ length: Math.min(5, last) }, (_, i) => start + i)
  const item = (target: number, content: ReactNode, aria: string, current = false) => {
    const className = `inline-flex size-10 items-center justify-center rounded-lg text-sm font-semibold transition-colors ${
      current ? 'bg-primary text-primary-foreground' : 'text-foreground hover:bg-muted'
    }`
    const disabled = target < 1 || target > last
    if (disabled)
      return (
        <span key={aria} className={className + ' pointer-events-none opacity-30'} aria-hidden="true">
          {content}
        </span>
      )
    return hrefFor ? (
      <Link
        key={aria}
        href={hrefFor(target)}
        className={className}
        aria-label={aria}
        aria-current={current ? 'page' : undefined}
      >
        {content}
      </Link>
    ) : (
      <button
        key={aria}
        type="button"
        className={className}
        aria-label={aria}
        aria-current={current ? 'page' : undefined}
        onClick={() => onChange?.(target)}
      >
        {content}
      </button>
    )
  }
  return (
    <nav aria-label={label} className="mt-10 flex items-center justify-center gap-1">
      {item(page - 1, <ChevronLeft className="size-4" />, '이전 페이지')}
      {pages.map((p) => item(p, p, p + '페이지', p === page))}
      {item(page + 1, <ChevronRight className="size-4" />, '다음 페이지')}
    </nav>
  )
}

export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div
      className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4 lg:gap-x-6"
      aria-hidden="true"
    >
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="flex flex-col gap-3">
          <div className="skeleton aspect-square" />
          <div className="skeleton h-3 w-1/3" />
          <div className="skeleton h-4 w-2/3" />
        </div>
      ))}
    </div>
  )
}

export function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton h-20" />
      ))}
    </div>
  )
}

/** 화면 전체를 불러오는 동안의 가운데 표시 */
export function PageLoading({ label = '불러오는 중' }: { label?: string }) {
  return (
    <div className="page page-narrow flex flex-col gap-4" role="status" aria-label={label}>
      <div className="skeleton h-8 w-1/3" />
      <div className="skeleton h-4 w-2/3" />
      <div className="skeleton mt-6 h-48" />
    </div>
  )
}
