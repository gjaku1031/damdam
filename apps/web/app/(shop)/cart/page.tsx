'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { ShoppingBag, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { useCartStore, useAuthStore } from '@/lib/store'
import { formatPrice } from '@/lib/data'
import { fetchProductById, storageUrl, type Product } from '@/lib/api'
import { useHydration } from '@/hooks/use-hydration'
import { QuantityStepper } from '@/components/quantity-stepper'
import { LoadError } from '@/components/operator-only'
import { EmptyState, ListSkeleton, PageHeader } from '@/components/ui-kit'

export default function CartPage() {
  const hydrated = useHydration(),
    router = useRouter()
  const user = useAuthStore((s) => s.user)
  const items = useCartStore((s) => s.items),
    updateQuantity = useCartStore((s) => s.updateQuantity),
    removeItem = useCartStore((s) => s.removeItem),
    addItem = useCartStore((s) => s.addItem)
  const [productMap, setProductMap] = useState<Record<string, Product | null>>({}),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0)
  const productIds = [...new Set(items.map((i) => i.productId))].sort().join(',')

  useEffect(() => {
    if (!hydrated) return
    let alive = true
    const ids = productIds ? productIds.split(',') : []
    setError('')
    if (ids.length === 0) {
      setLoading(false)
      return
    }
    setLoading(true)
    // 판매가 끝난 상품은 null로 두고 나머지는 계속 표시
    Promise.all(
      ids.map((id) =>
        fetchProductById(id)
          .then((p) => [id, p] as const)
          .catch((e) => {
            if (e?.status === 404) return [id, null] as const
            throw e
          }),
      ),
    )
      .then((rows) => {
        if (alive) setProductMap(Object.fromEntries(rows))
      })
      .catch((e) => {
        if (alive) setError(e.message)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [hydrated, productIds, retry])

  if (!hydrated || (loading && items.length > 0))
    return (
      <div className="page">
        <PageHeader title="장바구니" />
        <ListSkeleton rows={3} />
      </div>
    )
  if (error)
    return (
      <div className="page">
        <PageHeader title="장바구니" />
        <LoadError message={error} onRetry={() => setRetry((n) => n + 1)} />
      </div>
    )
  if (items.length === 0)
    return (
      <div className="page page-narrow">
        <PageHeader title="장바구니" />
        <EmptyState
          icon={<ShoppingBag className="size-5" />}
          title="장바구니가 비어 있습니다"
          description="마음에 드는 상품을 담아보세요."
          action={
            <Link href="/products" className="button">
              상품 둘러보기
            </Link>
          }
        />
      </div>
    )

  const lines = items.map((item) => {
    const product = productMap[item.productId]
    const variant = product?.variants.find((v) => v.id === item.variantId)
    return { ...item, product, variant }
  })
  const orderable = lines.filter((l) => l.product && l.variant && l.variant.stock > 0)
  const blocked = lines.some(
    (l) => !l.product || !l.variant || l.variant.stock <= 0 || l.quantity > l.variant.stock,
  )
  const total = orderable.reduce((sum, l) => sum + (l.variant?.priceAmount ?? 0) * l.quantity, 0),
    count = orderable.reduce((n, l) => n + l.quantity, 0)

  function remove(line: (typeof lines)[number]) {
    removeItem(line.productId, line.variantId)
    toast('장바구니에서 뺐습니다.', {
      action: {
        label: '되돌리기',
        onClick: () =>
          addItem({ productId: line.productId, variantId: line.variantId, quantity: line.quantity }),
      },
    })
  }
  const checkoutHref = user ? '/checkout' : '/login?next=%2Fcheckout'
  const goCheckout = () => router.push(checkoutHref)

  return (
    <div className="page pb-0 lg:pb-24">
      <PageHeader title="장바구니" description={`${items.length}개 상품`} />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_360px] lg:gap-8">
        <ul className="card divide-y divide-border">
          {lines.map((line) => {
            const unavailable = !line.product || !line.variant || line.variant.stock <= 0
            return (
              <li key={line.variantId} className="flex gap-4 p-4 sm:p-5">
                <Link
                  href={'/products/' + line.productId}
                  className="relative size-20 shrink-0 overflow-hidden rounded-lg bg-muted sm:size-24"
                  tabIndex={-1}
                  aria-hidden="true"
                >
                  {line.product?.images[0] && (
                    <Image
                      fill
                      sizes="96px"
                      src={storageUrl(line.product.images[0])}
                      alt=""
                      className={`object-cover ${unavailable ? 'opacity-50' : ''}`}
                    />
                  )}
                </Link>
                <div className="flex min-w-0 flex-1 flex-col gap-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      {line.product ? (
                        <Link
                          href={'/products/' + line.productId}
                          className="line-clamp-2 font-semibold hover:text-primary"
                        >
                          {line.product.name}
                        </Link>
                      ) : (
                        <p className="font-semibold">판매가 끝난 상품</p>
                      )}
                      <p className="mt-0.5 text-sm text-muted-foreground">
                        {line.variant ? '옵션: ' + line.variant.name : '판매하지 않는 옵션'}
                      </p>
                      {line.variant && line.variant.stock > 0 && line.quantity > line.variant.stock && (
                        <p className="field-error">재고가 {line.variant.stock}개 남았습니다. 수량을 줄여주세요.</p>
                      )}
                      {line.variant && line.variant.stock <= 0 && (
                        <p className="field-error">품절되었습니다. 장바구니에서 빼주세요.</p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => remove(line)}
                      className="icon-button -mr-2 -mt-2 shrink-0 text-muted-foreground hover:text-destructive"
                      aria-label={(line.product?.name ?? '상품') + ' 빼기'}
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </div>
                  {!unavailable && line.variant && (
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <QuantityStepper
                        value={line.quantity}
                        max={line.variant.stock}
                        onChange={(n) => updateQuantity(line.productId, line.variantId, n)}
                        label={(line.product?.name ?? '') + ' 수량'}
                      />
                      <span className="text-base font-bold">
                        {formatPrice(line.variant.priceAmount * line.quantity)}
                      </span>
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>

        <aside className="card card-body flex flex-col gap-4 lg:sticky lg:top-[calc(var(--header-height)+24px)]">
          <h2 className="section-title">결제 금액</h2>
          <dl className="grid grid-cols-[1fr_auto] gap-y-2.5 text-[15px]">
            <dt className="text-muted-foreground">상품 금액 ({count}개)</dt>
            <dd>{formatPrice(total)}</dd>
            <dt className="text-muted-foreground">배송비</dt>
            <dd>무료</dd>
            <dt className="border-t border-border pt-4 font-bold">합계</dt>
            <dd className="border-t border-border pt-4 text-xl font-bold">{formatPrice(total)}</dd>
          </dl>
          {blocked && (
            <p className="notice-box">품절이거나 재고가 부족한 상품을 정리하면 주문할 수 있습니다.</p>
          )}
          <button className="button lg hidden lg:inline-flex" disabled={blocked || count === 0} onClick={goCheckout}>
            {user ? '주문하기' : '로그인하고 주문하기'}
          </button>
          <p className="text-[13px] leading-relaxed text-muted-foreground">
            결제는 무통장 입금으로 진행되며, 입금이 확인되면 제작·발송합니다.
          </p>
        </aside>
      </div>

      <div className="sticky bottom-0 z-40 -mx-5 mt-8 border-t border-border bg-background/95 px-5 md:-mx-8 md:px-8 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3 backdrop-blur lg:hidden">
        <button className="button lg w-full" disabled={blocked || count === 0} onClick={goCheckout}>
          {formatPrice(total)} {user ? '주문하기' : '로그인하고 주문하기'}
        </button>
      </div>
    </div>
  )
}
