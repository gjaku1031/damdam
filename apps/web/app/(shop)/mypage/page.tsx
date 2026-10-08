'use client'
import Link from 'next/link'
import { AlertTriangle, ArrowRight, Hammer, KeyRound, ReceiptText, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { useAuthStore } from '@/lib/store'
import { useApi } from '@/hooks/use-api'
import { mutate, type AdminDashboard, type ApiOrder } from '@/lib/api'
import { formatPrice } from '@/lib/data'
import { OrdersList } from '@/components/orders-list'
import { LoadError, MemberOnly } from '@/components/operator-only'
import { EmptyState, ListSkeleton } from '@/components/ui-kit'
import { AdminHeader, formatDateTime } from '@/components/admin-nav'
import { confirmAction } from '@/components/confirm-dialog'

export default function Page() {
  return (
    <MemberOnly>
      <Home />
    </MemberOnly>
  )
}

function Home() {
  const user = useAuthStore((s) => s.user)
  return user?.role === 'ADMIN' ? <Dashboard /> : <CustomerHome username={user?.username ?? ''} />
}

function CustomerHome({ username }: { username: string }) {
  const data = useApi<{ orders: ApiOrder[]; total: number }>('/orders/me?limit=3')
  return (
    <div className="page page-medium">
      <div className="page-header">
        <div>
          <p className="eyebrow mb-2">내 계정</p>
          <h1 className="page-title">{username}님, 안녕하세요</h1>
          <p className="page-description">주문 진행 상황과 계정 정보를 확인할 수 있습니다.</p>
        </div>
      </div>
      <div className="mb-10 grid gap-3 sm:grid-cols-2">
        <Link href="/mypage/orders" className="card flex items-center gap-4 p-5 hover:border-border-strong">
          <span className="flex size-11 items-center justify-center rounded-full bg-primary-soft text-primary">
            <ReceiptText className="size-5" aria-hidden="true" />
          </span>
          <span className="flex-1">
            <span className="block font-semibold">주문 내역</span>
            <span className="block text-sm text-muted-foreground">입금·배송 상태 확인, 주문 취소</span>
          </span>
          <ArrowRight className="size-4 text-muted-foreground" aria-hidden="true" />
        </Link>
        <Link href="/mypage/account" className="card flex items-center gap-4 p-5 hover:border-border-strong">
          <span className="flex size-11 items-center justify-center rounded-full bg-primary-soft text-primary">
            <KeyRound className="size-5" aria-hidden="true" />
          </span>
          <span className="flex-1">
            <span className="block font-semibold">계정 설정</span>
            <span className="block text-sm text-muted-foreground">비밀번호 변경, 회원 탈퇴</span>
          </span>
          <ArrowRight className="size-4 text-muted-foreground" aria-hidden="true" />
        </Link>
      </div>
      <div className="mb-4 flex items-end justify-between gap-3">
        <h2 className="section-title">최근 주문</h2>
        {!!data.data?.total && data.data.total > 3 && (
          <Link href="/mypage/orders" className="link text-sm">
            전체 {data.data.total}건 보기
          </Link>
        )}
      </div>
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {data.loading && <ListSkeleton rows={3} />}
      {data.data &&
        (data.data.orders.length ? (
          <OrdersList orders={data.data.orders} onChanged={data.reload} />
        ) : (
          <EmptyState
            icon={<ReceiptText className="size-5" />}
            title="아직 주문이 없습니다"
            description="마음에 드는 물건을 찾아보세요."
            action={
              <Link href="/products" className="button">
                상품 둘러보기
              </Link>
            }
          />
        ))}
    </div>
  )
}

function StatCard({
  href,
  icon,
  label,
  value,
  note,
  tone,
}: {
  href?: string
  icon: React.ReactNode
  label: string
  value: string
  note: string
  tone?: 'warning' | 'info' | 'danger'
}) {
  const toneClass = tone ? 'badge-' + tone : ''
  const body = (
    <>
      <span className={`badge self-start text-[13px] ${toneClass}`}>
        {icon}
        {label}
      </span>
      <span className="mt-1 text-[30px] font-bold leading-tight tracking-tight">{value}</span>
      <span className="text-[13px] text-muted-foreground">{note}</span>
    </>
  )
  return href ? (
    <Link href={href} className="card flex flex-col gap-1.5 p-5 transition-colors hover:border-border-strong">
      {body}
    </Link>
  ) : (
    <div className="card flex flex-col gap-1.5 p-5">{body}</div>
  )
}

function Dashboard() {
  const stats = useApi<AdminDashboard>('/admin/dashboard')
  const pending = useApi<{ orders: ApiOrder[]; total: number }>(
    '/admin/orders?status=PENDING_PAYMENT&limit=5&page=1',
  )
  const counts = stats.data?.orderCounts ?? {}

  async function confirmPaid(order: ApiOrder) {
    const ok = await confirmAction({
      title: '입금을 확인할까요?',
      description: `입금자 ${order.buyerName} · ${formatPrice(order.finalPaymentAmount)}. 통장 내역과 이름·금액이 맞는지 확인해주세요.`,
      confirmLabel: '입금 확인',
    })
    if (!ok) return
    try {
      await mutate('/admin/orders/' + order.id + '/status', 'PATCH', {
        status: 'PAID',
        reason: '입금 확인',
      })
      toast.success(`${order.orderNo} 입금을 확인했습니다.`)
      pending.reload()
      stats.reload()
    } catch (e) {
      toast.error((e as Error).message)
    }
  }

  return (
    <div className="page">
      <AdminHeader title="오늘 할 일" description="입금 확인과 발송 준비가 필요한 주문부터 처리하세요." />
      {stats.error && <LoadError message={stats.error} onRetry={stats.reload} />}
      <div className="mb-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {stats.loading || !stats.data ? (
          Array.from({ length: 4 }, (_, i) => <div key={i} className="skeleton h-[132px]" aria-hidden="true" />)
        ) : (
          <>
            <StatCard
              href="/mypage/orders?status=PENDING_PAYMENT"
              icon={<Wallet className="size-4" aria-hidden="true" />}
              label="입금 확인 대기"
              value={`${counts.PENDING_PAYMENT ?? 0}건`}
              note="통장 내역과 맞춰 확인하세요"
              tone="warning"
            />
            <StatCard
              href="/mypage/orders?status=PAID"
              icon={<Hammer className="size-4" aria-hidden="true" />}
              label="제작·발송 준비"
              value={`${(counts.PAID ?? 0) + (counts.PROCESSING ?? 0)}건`}
              note={`입금 확인 ${counts.PAID ?? 0} · 제작·배송 중 ${counts.PROCESSING ?? 0}`}
              tone="info"
            />
            <StatCard
              icon={<ReceiptText className="size-4" aria-hidden="true" />}
              label="오늘 매출"
              value={formatPrice(stats.data.todaySalesAmount)}
              note={`오늘 주문 ${stats.data.todayOrderCount}건 · 이번 달 ${formatPrice(stats.data.monthSalesAmount)}`}
            />
            <StatCard
              icon={<AlertTriangle className="size-4" aria-hidden="true" />}
              label="재고 부족"
              value={stats.data.lowStock.length >= 10 ? '10개 이상' : `${stats.data.lowStock.length}개 옵션`}
              note="3개 이하로 남은 옵션"
              tone={stats.data.lowStock.length ? 'danger' : undefined}
            />
          </>
        )}
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-[2fr_1fr]">
        <section className="card overflow-hidden" aria-labelledby="pending-title">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <h2 id="pending-title" className="text-[17px] font-bold">
              입금 확인 대기 주문
            </h2>
            <Link href="/mypage/orders" className="link text-sm">
              전체 주문
            </Link>
          </div>
          {pending.error && (
            <div className="px-5">
              <LoadError message={pending.error} onRetry={pending.reload} />
            </div>
          )}
          {pending.loading && (
            <div className="p-5">
              <ListSkeleton rows={3} />
            </div>
          )}
          {pending.data && pending.data.orders.length === 0 && (
            <p className="px-5 py-10 text-center text-sm text-muted-foreground">
              입금을 기다리는 주문이 없습니다.
            </p>
          )}
          <ul>
            {pending.data?.orders.map((order) => (
              <li
                key={order.id}
                className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 border-b border-border px-5 py-4 last:border-b-0 sm:grid-cols-[1.6fr_1fr_auto_auto]"
              >
                <div className="min-w-0">
                  <p className="truncate text-[15px] font-semibold">{order.orderNo}</p>
                  <p className="truncate text-[13px] text-muted-foreground">
                    {order.items[0]?.productNameSnapshot ?? '상품'}
                    {order.items.length > 1 && ` 외 ${order.items.length - 1}건`}
                  </p>
                </div>
                <div className="hidden min-w-0 sm:block">
                  <p className="truncate text-sm">입금자 {order.buyerName}</p>
                  <p className="text-[13px] text-muted-foreground">{formatDateTime(order.createdAt)}</p>
                </div>
                <p className="text-right text-[15px] font-bold">{formatPrice(order.finalPaymentAmount)}</p>
                <button type="button" className="button sm col-span-2 sm:col-span-1" onClick={() => void confirmPaid(order)}>
                  입금 확인
                </button>
              </li>
            ))}
          </ul>
          {!!pending.data && pending.data.total > pending.data.orders.length && (
            <Link
              href="/mypage/orders?status=PENDING_PAYMENT"
              className="block border-t border-border px-5 py-3 text-center text-sm font-semibold text-primary hover:bg-muted/50"
            >
              대기 주문 {pending.data.total}건 모두 보기
            </Link>
          )}
        </section>

        <section className="card overflow-hidden" aria-labelledby="stock-title">
          <div className="border-b border-border px-5 py-4">
            <h2 id="stock-title" className="text-[17px] font-bold">
              재고 부족
            </h2>
          </div>
          {stats.data && stats.data.lowStock.length === 0 && (
            <p className="px-5 py-10 text-center text-sm text-muted-foreground">
              재고가 부족한 옵션이 없습니다.
            </p>
          )}
          {stats.loading && (
            <div className="p-5">
              <ListSkeleton rows={2} />
            </div>
          )}
          <ul>
            {stats.data?.lowStock.map((item) => (
              <li key={item.variantId} className="border-b border-border last:border-b-0">
                <Link
                  href={'/products/' + item.productId}
                  className="flex items-center justify-between gap-3 px-5 py-3.5 text-sm hover:bg-muted/50"
                >
                  <span className="min-w-0 truncate">
                    {item.productName} · {item.variantName}
                  </span>
                  <span className={`shrink-0 font-bold ${item.stock === 0 ? 'text-destructive' : ''}`}>
                    {item.stock === 0 ? '품절' : `${item.stock}개`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  )
}
