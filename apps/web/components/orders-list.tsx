'use client'
import { useState } from 'react'
import { ChevronDown, Package } from 'lucide-react'
import { toast } from 'sonner'
import { useApi } from '@/hooks/use-api'
import { mutate, storageUrl, type ApiOrder, type SiteInfo } from '@/lib/api'
import { formatPrice } from '@/lib/data'
import { orderActionLabels, orderStatus, orderTransitions } from '@/lib/order-status'
import { confirmAction } from './confirm-dialog'
import { StatusBadge } from './ui-kit'
import { formatDateTime } from './admin-nav'

/** 예전 화면 호환용 상태 이름 */
export const orderLabels: Record<string, string> = Object.fromEntries(
  Object.entries(orderStatus).map(([key, value]) => [key, value.label]),
)

const dangerStatuses = new Set(['CANCELED', 'PARTIALLY_REFUNDED', 'FULLY_REFUNDED'])

export function OrdersList({
  orders,
  operations = false,
  onChanged,
}: {
  orders: ApiOrder[]
  operations?: boolean
  onChanged: () => void
}) {
  return (
    <ul className="flex flex-col gap-3">
      {orders.map((order) => (
        <li key={order.id}>
          <OrderRow order={order} operations={operations} onChanged={onChanged} />
        </li>
      ))}
    </ul>
  )
}

function itemSummary(order: ApiOrder) {
  const first = order.items[0]
  if (!first) return '주문 상품 없음'
  return order.items.length > 1
    ? `${first.productNameSnapshot} 외 ${order.items.length - 1}건`
    : first.productNameSnapshot
}

function OrderRow({
  order,
  operations,
  onChanged,
}: {
  order: ApiOrder
  operations: boolean
  onChanged: () => void
}) {
  const [open, setOpen] = useState(false),
    [reason, setReason] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  const detail = useApi<ApiOrder>(
    open ? (operations ? '/admin/orders/' : '/orders/') + order.id : null,
  )
  const pendingCustomer = !operations && order.status === 'PENDING_PAYMENT'
  const site = useApi<SiteInfo>(open && pendingCustomer ? '/site' : null)
  const first = order.items[0]
  const panelId = 'order-panel-' + order.id
  const itemsTotal = order.items.reduce((n, i) => n + i.unitPriceAmount * i.quantity, 0)
  const histories = [...(detail.data?.statusHistories ?? [])].sort((a, b) =>
    a.createdAt.localeCompare(b.createdAt),
  )

  async function changeStatus(to: string) {
    const label = orderActionLabels[to] ?? orderStatus[to]?.label ?? to
    const ok = await confirmAction({
      title: `${label} 처리할까요?`,
      description:
        to === 'PAID'
          ? `입금자 ${order.buyerName} · ${formatPrice(order.finalPaymentAmount)} 입금을 확인했는지 통장 내역과 맞춰보세요.`
          : `주문 ${order.orderNo}의 상태를 '${orderStatus[to]?.label ?? to}'(으)로 바꿉니다.`,
      confirmLabel: label,
      tone: dangerStatuses.has(to) ? 'danger' : 'default',
    })
    if (!ok) return
    setBusy(true)
    setError('')
    try {
      await mutate('/admin/orders/' + order.id + '/status', 'PATCH', {
        status: to,
        reason: reason.trim() || label,
      })
      setReason('')
      toast.success(`${label} 처리했습니다.`)
      detail.reload()
      onChanged()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function cancel() {
    const ok = await confirmAction({
      title: '주문을 취소할까요?',
      description: '취소하면 되돌릴 수 없습니다. 이미 입금하셨다면 고객센터로 연락해주세요.',
      confirmLabel: '주문 취소',
      cancelLabel: '닫기',
      tone: 'danger',
    })
    if (!ok) return
    setBusy(true)
    setError('')
    try {
      await mutate('/orders/' + order.id + '/cancel', 'POST')
      toast.success('주문을 취소했습니다.')
      detail.reload()
      onChanged()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const next = orderTransitions[order.status] ?? []
  return (
    <div className="card overflow-hidden">
      <button
        type="button"
        className="flex w-full items-center gap-4 p-4 text-left transition-colors hover:bg-muted/50 sm:p-5"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="relative size-14 shrink-0 overflow-hidden rounded-lg bg-muted sm:size-16">
          {first?.thumbnailImageUrlSnapshot ? (
            <img
              src={storageUrl(first.thumbnailImageUrlSnapshot)}
              alt=""
              className="size-full object-cover"
            />
          ) : (
            <Package className="absolute inset-0 m-auto size-5 text-subtle-foreground" aria-hidden="true" />
          )}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="flex flex-wrap items-center gap-2">
            <StatusBadge status={order.status} />
            {order.orderNo.startsWith('DEMO-') && <span className="badge">체험용</span>}
          </span>
          <span className="truncate text-[15px] font-semibold">{itemSummary(order)}</span>
          <span className="truncate text-[13px] text-muted-foreground">
            {formatDateTime(order.createdAt, operations)} · {order.orderNo}
            {operations && ` · ${order.buyerName}${order.user ? ` (${order.user.username})` : ''}`}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2">
          <span className="text-[15px] font-bold">{formatPrice(order.finalPaymentAmount)}</span>
          <ChevronDown
            className={`size-5 text-muted-foreground transition-transform ${open ? 'rotate-180' : ''}`}
            aria-hidden="true"
          />
        </span>
      </button>

      {open && (
        <div id={panelId} className="flex flex-col gap-6 border-t border-border p-4 sm:p-6">
          {pendingCustomer && (
            <div className="notice-box">
              <p className="font-semibold">
                {formatPrice(order.finalPaymentAmount)}을 입금해주세요
              </p>
              <p className="mt-1 whitespace-pre-wrap">
                {site.loading
                  ? '입금 계좌를 불러오는 중…'
                  : site.data?.bankInstructions || '입금 계좌는 고객센터로 문의해주세요.'}
              </p>
              <p className="mt-1 text-muted-foreground">
                입금자명은 주문자 이름({order.buyerName})과 같게 해주세요.
              </p>
            </div>
          )}

          <section aria-label="주문 상품">
            <ul className="flex flex-col gap-3">
              {order.items.map((item) => (
                <li key={item.id} className="flex items-center gap-3">
                  <span className="relative size-12 shrink-0 overflow-hidden rounded-lg bg-muted">
                    {item.thumbnailImageUrlSnapshot && (
                      <img
                        src={storageUrl(item.thumbnailImageUrlSnapshot)}
                        alt=""
                        className="size-full object-cover"
                      />
                    )}
                  </span>
                  <span className="min-w-0 flex-1 text-sm">
                    <span className="block truncate font-medium">{item.productNameSnapshot}</span>
                    <span className="block text-muted-foreground">
                      {item.variantNameSnapshot} · {formatPrice(item.unitPriceAmount)} × {item.quantity}
                    </span>
                  </span>
                  <span className="text-sm font-semibold">
                    {formatPrice(item.unitPriceAmount * item.quantity)}
                  </span>
                </li>
              ))}
            </ul>
          </section>

          <div className="grid gap-4 md:grid-cols-2">
            <section className="rounded-xl bg-muted/60 p-4 text-sm">
              <h3 className="mb-2 font-semibold">배송 정보</h3>
              <p>
                {order.shipName} · {order.shipPhone}
              </p>
              <p className="mt-1 text-muted-foreground">
                ({order.shipPostalCode}) {order.shipAddress1} {order.shipAddress2}
              </p>
              {order.shipMemo && <p className="mt-1 text-muted-foreground">메모: {order.shipMemo}</p>}
              {operations && (
                <p className="mt-2 text-muted-foreground">
                  주문자 {order.buyerName} · {order.buyerPhone}
                </p>
              )}
            </section>
            <section className="rounded-xl bg-muted/60 p-4 text-sm">
              <h3 className="mb-2 font-semibold">결제 정보</h3>
              <dl className="grid grid-cols-[1fr_auto] gap-y-1.5">
                <dt className="text-muted-foreground">결제 수단</dt>
                <dd>무통장 입금</dd>
                <dt className="text-muted-foreground">상품 금액</dt>
                <dd>{formatPrice(itemsTotal)}</dd>
                <dt className="text-muted-foreground">배송비</dt>
                <dd>{order.shippingCost ? formatPrice(order.shippingCost) : '무료'}</dd>
                <dt className="font-semibold">결제 금액</dt>
                <dd className="font-semibold">{formatPrice(order.finalPaymentAmount)}</dd>
                {order.refundedAmount > 0 && (
                  <>
                    <dt className="text-destructive">환불 금액</dt>
                    <dd className="text-destructive">{formatPrice(order.refundedAmount)}</dd>
                  </>
                )}
              </dl>
            </section>
          </div>

          <section aria-label="진행 이력">
            <h3 className="mb-3 text-sm font-semibold">진행 이력</h3>
            {detail.error ? (
              <p role="alert" className="text-sm text-destructive">
                이력을 불러오지 못했습니다.{' '}
                <button type="button" className="link" onClick={detail.reload}>
                  다시 시도
                </button>
              </p>
            ) : detail.loading ? (
              <div className="skeleton h-16" aria-hidden="true" />
            ) : (
              <ol className="ml-1.5 flex flex-col gap-3 border-l border-border pl-5">
                {histories.map((history) => (
                  <li key={history.id} className="relative text-sm">
                    <span
                      className="absolute -left-[25px] top-1.5 size-2 rounded-full bg-primary"
                      aria-hidden="true"
                    />
                    <span className="font-medium">{orderStatus[history.toStatus]?.label ?? history.toStatus}</span>
                    <span className="ml-2 text-[13px] text-muted-foreground">
                      {formatDateTime(history.createdAt)}
                    </span>
                    {history.reason && (
                      <p className="mt-0.5 text-[13px] text-muted-foreground">{history.reason}</p>
                    )}
                  </li>
                ))}
              </ol>
            )}
          </section>

          {operations && next.length > 0 && (
            <section aria-label="주문 처리" className="flex flex-col gap-3 border-t border-border pt-5">
              <label className="field">
                처리 메모 (선택)
                <input
                  className="control"
                  value={reason}
                  maxLength={200}
                  placeholder="예: 10/8 입금 확인, 송장 123-456"
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <div className="flex flex-wrap gap-2">
                {next.map((to) => (
                  <button
                    key={to}
                    type="button"
                    disabled={busy}
                    className={
                      dangerStatuses.has(to)
                        ? 'button danger'
                        : to === next[0]
                          ? 'button'
                          : 'button secondary'
                    }
                    onClick={() => void changeStatus(to)}
                  >
                    {orderActionLabels[to] ?? orderStatus[to]?.label ?? to}
                  </button>
                ))}
              </div>
            </section>
          )}

          {pendingCustomer && (
            <div className="flex justify-end">
              <button type="button" className="button danger" disabled={busy} onClick={() => void cancel()}>
                주문 취소
              </button>
            </div>
          )}

          {error && (
            <p role="alert" className="field-error">
              {error}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
