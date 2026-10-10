'use client'
import { use, useEffect, useState } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { ChevronRight, PackageX } from 'lucide-react'
import { toast } from 'sonner'
import { AppSelect } from '@/components/app-select'
import { useApi } from '@/hooks/use-api'
import { useAuthStore, useCartStore } from '@/lib/store'
import { storageUrl, type ApiProduct, type ApiProductVariant } from '@/lib/api'
import { ProductEditor } from '@/components/product-editor'
import { QuantityStepper } from '@/components/quantity-stepper'
import { EmptyState } from '@/components/ui-kit'
import { formatPrice } from '@/lib/data'

function stockNote(variant: ApiProductVariant) {
  if (variant.stock <= 0) return '품절'
  if (variant.stock <= 3) return variant.stock + '개 남음'
  return ''
}

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params),
    router = useRouter(),
    edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN'),
    add = useCartStore((s) => s.addItem),
    cartItems = useCartStore((s) => s.items)
  const result = useApi<ApiProduct>((edit ? '/admin/products/' : '/products/') + id)
  const [editing, setEditing] = useState(false),
    [selected, setSelected] = useState(''),
    [quantity, setQuantity] = useState(1),
    [photo, setPhoto] = useState(0)
  const product = result.data,
    variants = product?.variants.filter((v) => v.active !== false) ?? [],
    variant =
      variants.find((v) => v.id === selected) ??
      variants.find((v) => v.isDefault && v.stock > 0) ??
      variants.find((v) => v.stock > 0) ??
      variants[0]
  useEffect(() => {
    setQuantity(1)
    setPhoto(0)
    setSelected('')
  }, [id])

  if (result.error)
    return (
      <div className="page page-narrow">
        <EmptyState
          icon={<PackageX className="size-5" />}
          title="상품을 찾을 수 없습니다"
          description="판매가 끝났거나 주소가 바뀌었을 수 있습니다. 연결 문제라면 다시 시도해주세요."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <button className="button secondary" onClick={result.reload}>
                다시 시도
              </button>
              <Link href="/products" className="button">
                상품 목록
              </Link>
            </div>
          }
        />
      </div>
    )
  if (!product)
    return (
      <div className="page" aria-busy="true" aria-label="상품 불러오는 중">
        <div className="grid gap-10 md:grid-cols-[7fr_5fr]">
          <div className="skeleton aspect-square rounded-2xl" />
          <div className="flex flex-col gap-4">
            <div className="skeleton h-4 w-24" />
            <div className="skeleton h-9 w-2/3" />
            <div className="skeleton h-7 w-32" />
            <div className="skeleton mt-4 h-24" />
            <div className="skeleton h-12" />
          </div>
        </div>
      </div>
    )

  const images = [...product.images].sort((a, b) => a.sortOrder - b.sortOrder)
  const inCart = variant
    ? (cartItems.find((i) => i.variantId === variant.id)?.quantity ?? 0)
    : 0
  const available = Math.max(0, (variant?.stock ?? 0) - inCart)
  const purchasable = product.visible && Boolean(variant) && available > 0
  const price = variant?.priceAmount ?? 0

  function put() {
    if (!variant || quantity > available) {
      toast.error(
        available > 0
          ? `지금 담을 수 있는 수량은 ${available}개입니다.`
          : '이 옵션은 더 담을 수 없습니다.',
      )
      return false
    }
    add({ productId: id, variantId: variant.id, quantity })
    return true
  }
  function addToCart() {
    if (!put()) return
    toast.success('장바구니에 담았습니다.', {
      action: { label: '장바구니 보기', onClick: () => router.push('/cart') },
    })
    setQuantity(1)
  }
  function buyNow() {
    if (put()) router.push('/checkout')
  }
  const soldOutLabel = !product.visible ? '판매 중지' : '품절'

  return (
    <div className="page pb-0 md:pb-24">
      <nav aria-label="위치" className="mb-6 flex items-center gap-1 text-sm text-muted-foreground">
        <Link href="/products" className="hover:text-foreground">
          상품
        </Link>
        {product.category && (
          <>
            <ChevronRight className="size-3.5" aria-hidden="true" />
            <Link
              href={'/products?category=' + encodeURIComponent(product.category)}
              className="hover:text-foreground"
            >
              {product.category}
            </Link>
          </>
        )}
      </nav>
      <div className="grid items-start gap-8 md:grid-cols-[7fr_5fr] md:gap-14">
        <div className="flex flex-col gap-3">
          <div className="relative aspect-square overflow-hidden rounded-2xl bg-muted">
            {images[photo] && (
              <Image
                fill
                priority
                sizes="(max-width:768px) 100vw, 58vw"
                className="object-cover"
                src={storageUrl(images[photo].objectKey)}
                alt={`${product.name} 사진 ${photo + 1}`}
              />
            )}
          </div>
          {images.length > 1 && (
            <div className="grid grid-cols-5 gap-2.5">
              {images.map((image, index) => (
                <button
                  key={image.id}
                  type="button"
                  className={`relative aspect-square overflow-hidden rounded-lg border-2 ${
                    index === photo ? 'border-primary' : 'border-transparent opacity-80 hover:opacity-100'
                  }`}
                  aria-label={`사진 ${index + 1} 보기`}
                  aria-pressed={index === photo}
                  onClick={() => setPhoto(index)}
                >
                  <Image
                    fill
                    sizes="120px"
                    src={storageUrl(image.objectKey)}
                    alt=""
                    className="object-cover"
                  />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex flex-col gap-6 md:sticky md:top-[calc(var(--header-height)+24px)]">
          <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-2">
              {product.category && <span className="eyebrow">{product.category}</span>}
              {!product.visible && <span className="badge badge-warning">비공개</span>}
            </div>
            <h1 className="text-[28px] font-bold leading-tight md:text-[32px]">{product.name}</h1>
            <p className="mt-1 text-[26px] font-bold">{formatPrice(price)}</p>
          </div>
          {product.description && (
            <p className="whitespace-pre-wrap leading-relaxed text-foreground/85">
              {product.description}
            </p>
          )}

          {variants.length > 0 && (
            <div className="flex flex-col gap-2">
              <p id="option-label" className="text-sm font-semibold">
                옵션
              </p>
              {variants.length <= 8 ? (
                <div role="radiogroup" aria-labelledby="option-label" className="grid grid-cols-2 gap-2">
                  {variants.map((v) => {
                    const active = v.id === variant?.id,
                      note = stockNote(v)
                    return (
                      <button
                        key={v.id}
                        type="button"
                        role="radio"
                        aria-checked={active}
                        disabled={v.stock <= 0}
                        onClick={() => {
                          setSelected(v.id)
                          setQuantity(1)
                        }}
                        className={`flex min-h-14 flex-col items-start justify-center rounded-[10px] bg-card px-3.5 py-2.5 text-left disabled:cursor-not-allowed disabled:opacity-45 ${
                          active ? 'border-2 border-primary' : 'border border-border-strong hover:border-muted-foreground'
                        }`}
                      >
                        <span className="text-[15px] font-semibold">{v.name}</span>
                        <span className="text-[13px] text-muted-foreground">
                          {formatPrice(v.priceAmount)}
                          {note && <span className={v.stock <= 0 ? '' : ' font-semibold text-primary'}> · {note}</span>}
                        </span>
                      </button>
                    )
                  })}
                </div>
              ) : (
                <AppSelect
                  label="옵션"
                  value={variant?.id ?? ''}
                  onValueChange={(value) => {
                    setSelected(value)
                    setQuantity(1)
                  }}
                  options={variants.map((v) => ({
                    value: v.id,
                    label: `${v.name} · ${formatPrice(v.priceAmount)}`,
                    description: stockNote(v) || undefined,
                  }))}
                />
              )}
            </div>
          )}

          <div className="flex items-center justify-between gap-4">
            <span className="text-sm font-semibold">수량</span>
            <QuantityStepper
              value={quantity}
              max={Math.max(1, available)}
              onChange={setQuantity}
              disabled={!purchasable}
            />
          </div>
          {inCart > 0 && (
            <p className="-mt-3 text-right text-[13px] text-muted-foreground">
              장바구니에 {inCart}개 담겨 있습니다
            </p>
          )}
          <div className="flex items-baseline justify-between border-t border-border pt-5">
            <span className="text-[15px] text-muted-foreground">합계</span>
            <span className="text-2xl font-bold">{formatPrice(price * quantity)}</span>
          </div>
          <div className="hidden grid-cols-2 gap-2.5 md:grid">
            <button className="button secondary lg" disabled={!purchasable} onClick={addToCart}>
              장바구니 담기
            </button>
            <button className="button lg" disabled={!purchasable} onClick={buyNow}>
              {purchasable ? '바로 주문' : soldOutLabel}
            </button>
          </div>
          <dl className="card grid grid-cols-[88px_1fr] gap-y-2.5 p-5 text-sm">
            <dt className="text-muted-foreground">결제</dt>
            <dd>무통장 입금</dd>
            <dt className="text-muted-foreground">배송</dt>
            <dd>무료 · 입금 확인 후 제작·발송</dd>
            <dt className="text-muted-foreground">교환·반품</dt>
            <dd>
              수령 후 7일 안{' '}
              <Link href="/exchange-policy" className="link">
                자세히
              </Link>
            </dd>
          </dl>
          {edit && (
            <button className="button secondary" onClick={() => setEditing(true)}>
              상품·재고 수정
            </button>
          )}
        </div>
      </div>

      <div className="sticky bottom-0 z-40 -mx-5 mt-8 border-t border-border bg-background/95 px-5 md:-mx-8 md:px-8 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3 backdrop-blur md:hidden">
        <div className="flex items-center gap-2">
          <div className="mr-auto flex flex-col">
            <span className="text-xs text-muted-foreground">합계</span>
            <span className="text-lg font-bold">{formatPrice(price * quantity)}</span>
          </div>
          <button className="button secondary" disabled={!purchasable} onClick={addToCart}>
            담기
          </button>
          <button className="button" disabled={!purchasable} onClick={buyNow}>
            {purchasable ? '바로 주문' : soldOutLabel}
          </button>
        </div>
      </div>

      <ProductEditor
        open={editing}
        product={product}
        onClose={() => setEditing(false)}
        onSaved={result.reload}
      />
    </div>
  )
}
