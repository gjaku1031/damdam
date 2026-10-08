'use client'
import { useState } from 'react'
import { History as HistoryIcon } from 'lucide-react'
import { OperatorOnly, LoadError } from '@/components/operator-only'
import { AdminHeader, formatDateTime } from '@/components/admin-nav'
import { EmptyState, ListSkeleton, Pagination } from '@/components/ui-kit'
import { useApi } from '@/hooks/use-api'
import type { ApiAuditLog } from '@/lib/api'

const PAGE_SIZE = 50

/** API가 남기는 기록 대상 종류 (OperationsAuditAdvice, 상품·게시글·댓글 서비스) */
const targets = [
  { value: '', label: '전체' },
  { value: 'ORDERS', label: '주문' },
  { value: 'PRODUCT', label: '상품' },
  { value: 'INVENTORY', label: '재고' },
  { value: 'USERS', label: '회원' },
  { value: 'POST', label: '게시글' },
  { value: 'COMMENT', label: '댓글' },
  { value: 'CMS', label: '공지·FAQ' },
  { value: 'SYSTEM', label: '설정' },
]
const targetLabels: Record<string, string> = {
  ...Object.fromEntries(targets.filter((t) => t.value).map((t) => [t.value, t.label])),
  POSTS: '게시글 분류',
}
const actionLabels: Record<string, string> = { CREATE: '등록', UPDATE: '수정', DELETE: '삭제' }

export default function Page() {
  return (
    <OperatorOnly>
      <History />
    </OperatorOnly>
  )
}

function relative(value: string) {
  const minutes = Math.round((Date.now() - new Date(value).getTime()) / 60000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`
  return null
}

function History() {
  const [page, setPage] = useState(1),
    [target, setTarget] = useState('')
  const data = useApi<{ items: ApiAuditLog[]; total: number }>(
    `/admin/system/audit-logs?size=${PAGE_SIZE}&page=${page}${target ? '&target=' + target : ''}`,
  )
  return (
    <div className="page">
      <AdminHeader title="운영 기록" description="운영자가 바꾼 주문·상품·설정 내역입니다." />
      <div className="-mx-1 mb-5 overflow-x-auto px-1" role="group" aria-label="기록 대상으로 거르기">
        <div className="flex gap-2">
          {targets.map((t) => {
            const current = t.value === target
            return (
              <button
                key={t.value || 'all'}
                type="button"
                aria-pressed={current}
                onClick={() => {
                  setTarget(t.value)
                  setPage(1)
                }}
                className={`inline-flex min-h-10 shrink-0 items-center rounded-full border px-4 text-sm font-semibold transition-colors ${
                  current
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'border-border-strong bg-card text-foreground hover:border-muted-foreground'
                }`}
              >
                {t.label}
              </button>
            )
          })}
        </div>
      </div>
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {data.loading && <ListSkeleton rows={6} />}
      {data.data && data.data.items.length === 0 && (
        <EmptyState icon={<HistoryIcon className="size-5" />} title="기록이 없습니다" />
      )}
      {data.data && data.data.items.length > 0 && (
        <ul className="card divide-y divide-border">
          {data.data.items.map((item) => {
            const ago = relative(item.createdAt)
            return (
              <li key={item.id} className="flex flex-wrap items-start justify-between gap-x-6 gap-y-1 px-5 py-4">
                <div className="min-w-0">
                  <p className="flex flex-wrap items-center gap-2 text-[15px] font-medium">
                    <span className="badge">{targetLabels[item.targetType] ?? item.targetType}</span>
                    {item.description ?? actionLabels[item.action] ?? item.action}
                  </p>
                  <p className="mt-1 text-[13px] text-muted-foreground">
                    {item.actorUsername ?? '알 수 없는 운영자'}
                  </p>
                </div>
                <time
                  dateTime={item.createdAt}
                  title={formatDateTime(item.createdAt)}
                  className="shrink-0 text-[13px] text-muted-foreground"
                >
                  {ago ? `${ago} · ` : ''}
                  {formatDateTime(item.createdAt)}
                </time>
              </li>
            )
          })}
        </ul>
      )}
      {data.data && (
        <Pagination page={page} total={data.data.total} size={PAGE_SIZE} onChange={setPage} label="기록 페이지" />
      )}
    </div>
  )
}
