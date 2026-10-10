'use client'
import { Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { Check, CircleCheck, Copy, Landmark, Lock } from 'lucide-react'
import { toast } from 'sonner'
import { useApi } from '@/hooks/use-api'
import { useAuthStore } from '@/lib/store'
import { storageUrl, type ApiOrder, type SiteInfo } from '@/lib/api'
import { formatPrice } from '@/lib/data'
import { orderStatus } from '@/lib/order-status'
import { formatPaymentDue } from '@/lib/payment-due'
import { LoadError } from '@/components/operator-only'
import { EmptyState, PageLoading, StatusBadge } from '@/components/ui-kit'

const steps = [
  { key: 'PENDING_PAYMENT', label: '주문 접수' },
  { key: 'PAID', label: '입금 확인' },
  { key: 'PROCESSING', label: '제작·발송' },
  { key: 'COMPLETED', label: '배송 완료' },
]

function copy(text: string, label: string) {
  navigator.clipboard
    .writeText(text)
    .then(() => toast.success(label + '를 복사했습니다.'))
    .catch(() => toast.error('복사하지 못했습니다. 직접 선택해 복사해주세요.'))
}

function Progress({ status }: { status: string }) {
  const current = steps.findIndex((s) => s.key === status)
  if (current < 0) return null
  return (
    <ol className="grid grid-cols-4 gap-2" aria-label="주문 진행 단계">
      {steps.map((step, index) => {
        const done = index < current,
          active = index === current
        return (
          <li key={step.key} className="flex flex-col items-center gap-2 text-center">
            <span
              className={`flex size-8 items-center justify-center rounded-full text-sm font-bold ${
                done || active ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
              }`}
              aria-hidden="true"
            >
              {done ? <Check className="size-4" /> : index + 1}
            </span>
            <span
              className={`text-[13px] ${active ? 'font-bold text-foreground' : 'text-muted-foreground'}`}
              aria-current={active ? 'step' : undefined}
            >
              {step.label}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

function OrderResult() {
  const params = useSearchParams(),
    id = params.get('orderId'),
    user = useAuthStore((s) => s.user),
    ready = useAuthStore((s) => s.ready)
  const site = useApi<SiteInfo>('/site')
  const query = useApi<ApiOrder>(user && id ? '/orders/' + id : null)
  if (!ready) return <PageLoading label="로그인 확인 중" />
  if (!user)
    return (
      <div className="page page-narrow">
        <EmptyState
          icon={<Lock className="size-5" />}
          title="로그인 후 주문을 확인할 수 있습니다"
          action={
            <Link
              className="button"
              href={'/login?next=' + encodeURIComponent('/order-result?orderId=' + (id ?? ''))}
            >
              로그인
            </Link>
          }
        />
      </div>
    )
  if (!id)
    return (
      <div className="page page-narrow">
        <EmptyState
          title="주문 정보를 찾을 수 없습니다"
          action={
            <Link href="/mypage/orders" className="button secondary">
              주문 내역 보기
            </Link>
          }
        />
      </div>
    )
  if (query.error)
    return (
      <div className="page page-narrow">
        <LoadError message={query.error} onRetry={query.reload} />
      </div>
    )
  if (!query.data) return <PageLoading label="주문 확인 중" />
  const order = query.data,
    pending = order.status === 'PENDING_PAYMENT'
  const amount = order.finalPaymentAmount

  return (
    <div className="page page-narrow flex flex-col gap-6">
      <div className="flex flex-col items-center gap-3 pt-4 text-center">
        {pending ? (
          <CircleCheck className="size-12 text-primary" aria-hidden="true" />
        ) : (
          <StatusBadge status={order.status} />
        )}
        <h1 className="page-title">
          {pending ? '주문이 접수되었습니다' : orderStatus[order.status]?.label ?? '주문 내역'}
        </h1>
        <p className="text-muted-foreground">{orderStatus[order.status]?.description}</p>
        <div className="mt-1 flex items-center gap-1 text-sm">
          <span className="text-muted-foreground">주문번호</span>
          <span className="font-semibold">{order.orderNo}</span>
          <button
            type="button"
            className="icon-button size-9"
            aria-label="주문번호 복사"
            onClick={() => copy(order.orderNo, '주문번호')}
          >
            <Copy className="size-4" />
          </button>
        </div>
      </div>

      <div className="card card-body">
        <Progress status={order.status} />
      </div>

      {pending && order.paymentMethod === 'BANK_TRANSFER' && (
        <section className="card card-body flex flex-col gap-4" aria-labelledby="deposit-title">
          <h2 id="deposit-title" className="section-title flex items-center gap-2">
            <Landmark className="size-5 text-primary" aria-hidden="true" />
            입금 안내
          </h2>
          <dl className="grid grid-cols-[96px_1fr] gap-y-3 text-[15px]">
            <dt className="text-muted-foreground">입금할 금액</dt>
            <dd className="flex items-center gap-2 text-lg font-bold">
              {formatPrice(amount)}
              <button
                type="button"
                className="button ghost sm"
                onClick={() => copy(String(amount), '금액')}
              >
                복사
              </button>
            </dd>
            <dt className="text-muted-foreground">입금 계좌</dt>
            <dd className="whitespace-pre-wrap font-medium">
              {site.error ? (
                <span className="text-muted-foreground">계좌 정보를 불러오지 못했습니다.</span>
              ) : site.loading ? (
                <span className="skeleton block h-5 w-48" />
              ) : (
                site.data?.bankInstructions || '입금 계좌는 고객센터로 문의해주세요.'
              )}
            </dd>
            <dt className="text-muted-foreground">입금자명</dt>
            <dd className="font-medium">{order.buyerName}</dd>
            {order.paymentDueAt && (
              <>
                <dt className="text-muted-foreground">입금 기한</dt>
                <dd className="font-bold">{formatPaymentDue(order.paymentDueAt)}까지</dd>
              </>
            )}
          </dl>
          <p className="notice-box">
            입금자명을 주문자 이름과 같게 해주세요. 이름이 다르면 확인이 늦어질 수 있습니다.
            입금이 확인되면 제작·발송을 시작합니다.
            {order.paymentDueAt && ' 기한까지 입금이 확인되지 않으면 주문이 자동으로 취소됩니다.'}
          </p>
        </section>
      )}

      <section className="card" aria-labelledby="items-title">
        <h2 id="items-title" className="section-title px-6 pt-6">
          주문 상품
        </h2>
        <ul className="divide-y divide-border px-6">
          {order.items.map((item) => (
            <li key={item.id} className="flex items-center gap-3 py-4">
              <div className="size-14 shrink-0 overflow-hidden rounded-lg bg-muted">
                {item.thumbnailImageUrlSnapshot && (
                  <img
                    className="size-full object-cover"
                    src={storageUrl(item.thumbnailImageUrlSnapshot, 's')}
                    alt=""
                  />
                )}
              </div>
              <div className="min-w-0 flex-1 text-sm">
                <p className="font-semibold">{item.productNameSnapshot}</p>
                <p className="text-muted-foreground">
                  {item.variantNameSnapshot} · {item.quantity}개
                </p>
              </div>
              <span className="text-sm font-semibold">
                {formatPrice(item.unitPriceAmount * item.quantity)}
              </span>
            </li>
          ))}
        </ul>
        <dl className="grid grid-cols-[1fr_auto] gap-y-2 border-t border-border px-6 py-5 text-sm">
          <dt className="text-muted-foreground">배송비</dt>
          <dd>{order.shippingCost ? formatPrice(order.shippingCost) : '무료'}</dd>
          <dt className="font-bold">결제 금액</dt>
          <dd className="font-bold">{formatPrice(amount)}</dd>
        </dl>
      </section>

      <section className="card card-body" aria-labelledby="ship-title">
        <h2 id="ship-title" className="section-title mb-4">
          배송 정보
        </h2>
        <dl className="grid grid-cols-[88px_1fr] gap-y-2.5 text-sm">
          <dt className="text-muted-foreground">받는 분</dt>
          <dd>
            {order.shipName} · {order.shipPhone}
          </dd>
          <dt className="text-muted-foreground">주소</dt>
          <dd>
            ({order.shipPostalCode}) {order.shipAddress1} {order.shipAddress2}
          </dd>
          {order.shipMemo && (
            <>
              <dt className="text-muted-foreground">배송 메모</dt>
              <dd>{order.shipMemo}</dd>
            </>
          )}
        </dl>
      </section>

      <div className="grid gap-2 sm:grid-cols-2">
        <Link href="/mypage/orders" className="button lg">
          주문 내역 보기
        </Link>
        <Link href="/products" className="button secondary lg">
          계속 둘러보기
        </Link>
      </div>
    </div>
  )
}

export default function Page() {
  return (
    <Suspense fallback={<PageLoading label="주문 확인 중" />}>
      <OrderResult />
    </Suspense>
  )
}
