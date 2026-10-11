'use client'
import { useEffect, useMemo, useState } from 'react'
import { X } from 'lucide-react'
import { toast } from 'sonner'
import { useApi } from '@/hooks/use-api'
import { createProxyOrder, type ApiOrder, type ApiProduct } from '@/lib/api'
import { formatPrice } from '@/lib/data'
import { AppSelect } from './app-select'
import { confirmAction } from './confirm-dialog'
import { QuantityStepper } from './quantity-stepper'

/** 지금 주문받을 수 있는 옵션 하나 */
type Sellable = { variantId: string; name: string; price: number; stock: number }

/** 상품 목록 한 번에 불러오는 최대 개수 (API 상한), 이보다 많은 상품은 추가 목록에 나오지 않음 */
const PRODUCT_LIMIT = 100

/**
 * 운영자가 손님 대신 넣는 주문 양식
 *
 * 주문자·배송지는 원 주문 것을 서버가 그대로 쓰므로 여기서는 상품과 수량만 고름
 * 일부 반품은 원 주문을 전액 환불로 닫은 뒤 남길 상품만 이 양식으로 다시 넣음
 */
export function ProxyOrderForm({
  source,
  onCreated,
  onClose,
}: {
  source: ApiOrder
  onCreated: () => void
  onClose: () => void
}) {
  const products = useApi<{ products: ApiProduct[] }>(`/admin/products?limit=${PRODUCT_LIMIT}`)
  const [lines, setLines] = useState<{ variantId: string; quantity: number }[] | null>(null),
    [paid, setPaid] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')

  // 공개 상품의 판매 중 옵션만 주문 가능 (API의 주문 규칙과 같음)
  const sellable = useMemo(() => {
    const map = new Map<string, Sellable>()
    for (const product of products.data?.products ?? []) {
      if (!product.visible) continue
      for (const variant of product.variants) {
        if (!variant.active) continue
        map.set(variant.id, {
          variantId: variant.id,
          name: product.variants.length > 1 ? `${product.name} · ${variant.name}` : product.name,
          price: variant.priceAmount,
          stock: variant.stock,
        })
      }
    }
    return map
  }, [products.data])

  // 원 주문 상품 가운데 지금도 팔고 재고가 있는 것만 미리 담음, 수량은 재고를 넘지 않게
  useEffect(() => {
    if (!products.data || lines) return
    setLines(
      source.items.flatMap((item) => {
        const option = item.variant ? sellable.get(item.variant.id) : undefined
        return option && option.stock > 0
          ? [{ variantId: option.variantId, quantity: Math.min(item.quantity, option.stock) }]
          : []
      }),
    )
  }, [products.data, lines, sellable, source.items])

  const skipped = source.items.filter((item) => {
    const option = item.variant ? sellable.get(item.variant.id) : undefined
    return !option || option.stock <= 0
  })
  const chosen = new Set((lines ?? []).map((line) => line.variantId))
  const addable = [...sellable.values()].filter((option) => !chosen.has(option.variantId) && option.stock > 0)
  const total = (lines ?? []).reduce(
    (sum, line) => sum + (sellable.get(line.variantId)?.price ?? 0) * line.quantity,
    0,
  )
  const quantity = (lines ?? []).reduce((sum, line) => sum + line.quantity, 0)
  const customer = `${source.buyerName}${source.user ? ` (${source.user.username})` : ''}`

  async function submit() {
    if (!lines?.length) return
    const ok = await confirmAction({
      title: '새 주문을 만들까요?',
      description: `${customer} 님 앞으로 ${formatPrice(total)} 주문을 만들고 재고 ${quantity}개를 뺍니다. ${
        paid
          ? '받은 돈으로 처리해 바로 입금 확인 상태가 됩니다.'
          : '입금 대기 상태로 만들어지고 손님 주문 내역에 입금 안내가 보입니다.'
      }`,
      confirmLabel: '주문 만들기',
    })
    if (!ok) return
    setBusy(true)
    setError('')
    try {
      const created = await createProxyOrder({ sourceOrderId: source.id, items: lines, paid })
      toast.success(`주문 ${created.orderNo}을 만들었습니다.`)
      onCreated()
    } catch (e) {
      setError((e as Error).message)
      // 그사이 재고·판매 여부가 바뀌었을 수 있어 목록을 다시 받음
      products.reload()
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="새 주문 만들기" className="flex flex-col gap-4 rounded-xl border border-border p-4">
      <div>
        <h3 className="text-sm font-semibold">{customer} 님 앞으로 새 주문</h3>
        <p className="mt-1 text-[13px] leading-relaxed text-muted-foreground">
          주문자와 배송지는 이 주문과 같게 들어갑니다. 가격은 지금 판매가로 계산합니다.
        </p>
      </div>

      {products.error ? (
        <p role="alert" className="text-sm text-destructive">
          상품을 불러오지 못했습니다.{' '}
          <button type="button" className="link" onClick={products.reload}>
            다시 시도
          </button>
        </p>
      ) : !lines ? (
        <div className="skeleton h-16" aria-hidden="true" />
      ) : (
        <>
          {lines.length ? (
            <ul className="flex flex-col gap-3">
              {lines.map((line) => {
                const option = sellable.get(line.variantId)
                if (!option) return null
                return (
                  <li key={line.variantId} className="flex flex-wrap items-center gap-3">
                    <span className="min-w-0 flex-1 basis-40 text-sm">
                      <span className="block truncate font-medium">{option.name}</span>
                      <span className="block text-muted-foreground">
                        {formatPrice(option.price)} · 재고 {option.stock}개
                      </span>
                    </span>
                    <QuantityStepper
                      value={line.quantity}
                      max={option.stock}
                      label={`${option.name} 수량`}
                      disabled={busy}
                      onChange={(value) =>
                        setLines(
                          lines.map((l) => (l.variantId === line.variantId ? { ...l, quantity: value } : l)),
                        )
                      }
                    />
                    <button
                      type="button"
                      className="flex size-11 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
                      aria-label={`${option.name} 빼기`}
                      disabled={busy}
                      onClick={() => setLines(lines.filter((l) => l.variantId !== line.variantId))}
                    >
                      <X className="size-4" aria-hidden="true" />
                    </button>
                  </li>
                )
              })}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">담긴 상품이 없습니다. 아래에서 상품을 추가하세요.</p>
          )}

          {skipped.length > 0 && (
            <p className="text-[13px] text-muted-foreground">
              지금은 재고가 없거나 판매하지 않아 담지 못한 상품:{' '}
              {skipped.map((item) => item.productNameSnapshot).join(', ')}
            </p>
          )}

          {addable.length > 0 && (
            <AppSelect
              value=""
              label="상품 추가"
              placeholder="상품 추가"
              disabled={busy}
              options={addable.map((option) => ({
                value: option.variantId,
                label: option.name,
                description: `${formatPrice(option.price)} · 재고 ${option.stock}개`,
              }))}
              onValueChange={(variantId) => setLines([...lines, { variantId, quantity: 1 }])}
            />
          )}

          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1"
              checked={paid}
              disabled={busy}
              onChange={(e) => setPaid(e.target.checked)}
            />
            <span>
              이미 받은 돈으로 처리 (바로 입금 확인)
              <span className="block text-[13px] text-muted-foreground">
                체크를 풀면 입금 대기 주문으로 만들어지고 손님 주문 내역에 입금 안내가 보입니다.
              </span>
            </span>
          </label>

          <p className="flex items-center justify-between border-t border-border pt-3 text-sm">
            <span className="text-muted-foreground">합계 · {quantity}개</span>
            <span className="text-[15px] font-bold">{formatPrice(total)}</span>
          </p>
        </>
      )}

      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}

      <div className="flex flex-wrap justify-end gap-2">
        <button type="button" className="button secondary" disabled={busy} onClick={onClose}>
          닫기
        </button>
        <button
          type="button"
          className="button"
          disabled={busy || !lines?.length}
          aria-busy={busy}
          onClick={() => void submit()}
        >
          {busy ? '만드는 중…' : '주문 만들기'}
        </button>
      </div>
    </section>
  )
}
