'use client'
import { Suspense, useEffect, useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ReceiptText, Search } from 'lucide-react'
import { LoadError, MemberOnly } from '@/components/operator-only'
import { OrdersList } from '@/components/orders-list'
import { EmptyState, ListSkeleton, PageHeader, Pagination } from '@/components/ui-kit'
import { AdminHeader } from '@/components/admin-nav'
import { useApi } from '@/hooks/use-api'
import type { AdminDashboard, ApiOrder } from '@/lib/api'
import { orderStatus, orderStatusOrder } from '@/lib/order-status'
import { useAuthStore } from '@/lib/store'

const PAGE_SIZE = 20

export default function Page() {
  return (
    <MemberOnly>
      <Suspense>
        <Orders />
      </Suspense>
    </MemberOnly>
  )
}

function Orders() {
  const admin = useAuthStore((s) => s.user?.role === 'ADMIN')
  return admin ? <AdminOrders /> : <CustomerOrders />
}

function CustomerOrders() {
  const [page, setPage] = useState(1)
  const data = useApi<{ orders: ApiOrder[]; total: number }>(
    `/orders/me?limit=${PAGE_SIZE}&page=${page}`,
  )
  return (
    <div className="page page-medium">
      <PageHeader
        back={{ href: '/mypage', label: '내 계정' }}
        title="주문 내역"
        description="주문을 눌러 입금 안내, 배송 정보, 진행 이력을 확인하세요."
      />
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {data.loading && <ListSkeleton rows={4} />}
      {data.data &&
        (data.data.orders.length ? (
          <OrdersList orders={data.data.orders} onChanged={data.reload} />
        ) : (
          <EmptyState
            icon={<ReceiptText className="size-5" />}
            title="주문 내역이 없습니다"
            action={
              <Link href="/products" className="button">
                상품 둘러보기
              </Link>
            }
          />
        ))}
      {data.data && (
        <Pagination page={page} total={data.data.total} size={PAGE_SIZE} onChange={setPage} label="주문 페이지" />
      )}
    </div>
  )
}

function AdminOrders() {
  const params = useSearchParams(),
    router = useRouter(),
    pathname = usePathname()
  const status = params.get('status') ?? '',
    search = params.get('search') ?? '',
    page = Math.max(1, Number(params.get('page')) || 1)
  const [term, setTerm] = useState(search)
  useEffect(() => setTerm(search), [search])

  const query = new URLSearchParams({ limit: String(PAGE_SIZE), page: String(page) })
  if (status) query.set('status', status)
  if (search) query.set('search', search)
  const data = useApi<{ orders: ApiOrder[]; total: number }>('/admin/orders?' + query)
  const stats = useApi<AdminDashboard>('/admin/dashboard')
  const counts = stats.data?.orderCounts
  const totalCount = counts ? Object.values(counts).reduce((n, c) => n + c, 0) : null

  function go(next: { status?: string; search?: string; page?: number }) {
    const q = new URLSearchParams()
    const s = next.status ?? status,
      t = next.search ?? search,
      p = next.page ?? 1
    if (s) q.set('status', s)
    if (t) q.set('search', t)
    if (p > 1) q.set('page', String(p))
    router.push(pathname + (q.toString() ? '?' + q : ''), { scroll: false })
  }
  function reload() {
    data.reload()
    stats.reload()
  }
  const tabs = [
    { value: '', label: '전체', count: totalCount },
    ...orderStatusOrder.map((key) => ({
      value: key,
      label: orderStatus[key].label,
      count: counts?.[key] ?? null,
    })),
  ]

  return (
    <div className="page">
      <AdminHeader title="주문 관리" description="주문을 눌러 상세 내용을 보고 상태를 바꿀 수 있습니다." />
      <div className="mb-5 flex flex-col gap-4">
        <div className="-mx-1 overflow-x-auto px-1" role="group" aria-label="주문 상태로 거르기">
          <div className="flex gap-2">
            {tabs.map((tab) => {
              const current = tab.value === status
              return (
                <button
                  key={tab.value || 'all'}
                  type="button"
                  aria-pressed={current}
                  onClick={() => go({ status: tab.value, page: 1 })}
                  className={`inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-full border px-4 text-sm font-semibold transition-colors ${
                    current
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-border-strong bg-card text-foreground hover:border-muted-foreground'
                  }`}
                >
                  {tab.label}
                  {tab.count !== null && (
                    <span className={current ? 'opacity-80' : 'text-muted-foreground'}>{tab.count}</span>
                  )}
                </button>
              )
            })}
          </div>
        </div>
        <form
          role="search"
          className="flex max-w-xl gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            go({ search: term.trim(), page: 1 })
          }}
        >
          <label className="relative flex-1">
            <span className="sr-only">주문 검색</span>
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-subtle-foreground"
              aria-hidden="true"
            />
            <input
              type="search"
              className="control pl-10"
              placeholder="주문번호·이름·전화번호·아이디"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
            />
          </label>
          <button className="button secondary">검색</button>
        </form>
      </div>
      {search && (
        <p className="mb-4 text-sm text-muted-foreground">
          &lsquo;{search}&rsquo; 검색 결과 {data.data?.total ?? 0}건{' '}
          <button type="button" className="link ml-1" onClick={() => go({ search: '', page: 1 })}>
            검색 지우기
          </button>
        </p>
      )}
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {data.loading && <ListSkeleton rows={5} />}
      {data.data &&
        (data.data.orders.length ? (
          <OrdersList orders={data.data.orders} operations onChanged={reload} />
        ) : (
          <EmptyState
            icon={<ReceiptText className="size-5" />}
            title="해당하는 주문이 없습니다"
            description={status || search ? '다른 상태나 검색어로 찾아보세요.' : undefined}
          />
        ))}
      {data.data && (
        <Pagination
          page={page}
          total={data.data.total}
          size={PAGE_SIZE}
          onChange={(p) => go({ page: p })}
          label="주문 페이지"
        />
      )}
    </div>
  )
}
