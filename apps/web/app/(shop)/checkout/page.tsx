'use client'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import Image from 'next/image'
import { Landmark, Lock, MapPin, ShoppingBag } from 'lucide-react'
import { useAuthStore, useCartStore } from '@/lib/store'
import {
  ApiError,
  createOrder,
  fetchProductById,
  storageUrl,
  type ApiOrder,
  type CreateOrderInput,
  type Product,
  type SiteInfo,
} from '@/lib/api'
import { useApi } from '@/hooks/use-api'
import { formatPrice } from '@/lib/data'
import { searchAddress } from '@/lib/postcode'
import { LoadError } from '@/components/operator-only'
import { EmptyState, PageHeader, PageLoading } from '@/components/ui-kit'

type Form = Required<Omit<CreateOrderInput, 'items' | 'paymentMethod'>>
const initial: Form = {
  buyerName: '',
  buyerPhone: '',
  shipName: '',
  shipPhone: '',
  shipPostalCode: '',
  shipAddress1: '',
  shipAddress2: '',
  shipMemo: '',
}
const PHONE = /^0\d{1,2}-?\d{3,4}-?\d{4}$/

function validate(form: Form) {
  const issues: Partial<Record<keyof Form, string>> = {}
  if (!form.buyerName.trim()) issues.buyerName = '주문자 이름을 입력해주세요.'
  if (!PHONE.test(form.buyerPhone.trim())) issues.buyerPhone = '010-1234-5678 형식으로 입력해주세요.'
  if (!form.shipName.trim()) issues.shipName = '받는 분 이름을 입력해주세요.'
  if (!PHONE.test(form.shipPhone.trim())) issues.shipPhone = '010-1234-5678 형식으로 입력해주세요.'
  if (!/^\d{5}$/.test(form.shipPostalCode.trim())) issues.shipPostalCode = '우편번호 5자리를 입력해주세요.'
  if (!form.shipAddress1.trim()) issues.shipAddress1 = '주소를 입력해주세요.'
  return issues
}

export default function CheckoutPage() {
  const router = useRouter(),
    user = useAuthStore((s) => s.user),
    ready = useAuthStore((s) => s.ready),
    items = useCartStore((s) => s.items)
  const site = useApi<SiteInfo>('/site')
  const [form, setForm] = useState<Form>(initial),
    [sameAsBuyer, setSameAsBuyer] = useState(true),
    [agreed, setAgreed] = useState(false),
    [errors, setErrors] = useState<Partial<Record<keyof Form | 'agree', string>>>({}),
    [products, setProducts] = useState<Record<string, Product>>({}),
    [loading, setLoading] = useState(true),
    [loadError, setLoadError] = useState(''),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0),
    [busy, setBusy] = useState(false),
    [created, setCreated] = useState<ApiOrder | null>(null)
  const productIds = [...new Set(items.map((i) => i.productId))].sort().join(',')

  useEffect(() => {
    if (!ready || !user || !productIds) {
      setLoading(false)
      return
    }
    let alive = true
    setLoading(true)
    setLoadError('')
    Promise.all(productIds.split(',').map(async (id) => [id, await fetchProductById(id)] as const))
      .then((rows) => {
        if (alive) setProducts(Object.fromEntries(rows))
      })
      .catch((e) => {
        if (alive) setLoadError(e.message)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [productIds, ready, user, retry])

  if (!ready) return <PageLoading label="로그인 확인 중" />
  if (!user)
    return (
      <div className="page page-narrow">
        <EmptyState
          icon={<Lock className="size-5" />}
          title="주문하려면 로그인해주세요"
          description="장바구니에 담은 상품은 로그인한 뒤에도 그대로 남아 있습니다."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Link className="button" href="/login?next=%2Fcheckout">
                로그인
              </Link>
              <Link className="button secondary" href="/signup?next=%2Fcheckout">
                회원가입
              </Link>
            </div>
          }
        />
      </div>
    )
  if (!items.length && !created)
    return (
      <div className="page page-narrow">
        <EmptyState
          icon={<ShoppingBag className="size-5" />}
          title="주문할 상품이 없습니다"
          action={
            <Link href="/products" className="button">
              상품 둘러보기
            </Link>
          }
        />
      </div>
    )
  if (loading) return <PageLoading label="주문 상품 확인 중" />
  if (loadError)
    return (
      <div className="page page-narrow">
        <LoadError message={loadError} onRetry={() => setRetry((n) => n + 1)} />
      </div>
    )

  const lines = items.map((item) => ({
    ...item,
    product: products[item.productId],
    variant: products[item.productId]?.variants.find((v) => v.id === item.variantId),
  }))
  const invalid = lines.some((i) => !i.variant || i.variant.stock < i.quantity),
    total = lines.reduce((n, i) => n + (i.variant?.priceAmount ?? 0) * i.quantity, 0)
  const locked = busy || Boolean(created)

  function set(key: keyof Form, value: string) {
    setForm((old) => {
      const next = { ...old, [key]: value }
      if (sameAsBuyer && key === 'buyerName') next.shipName = value
      if (sameAsBuyer && key === 'buyerPhone') next.shipPhone = value
      return next
    })
    setErrors((old) => ({ ...old, [key]: undefined }))
  }
  async function findAddress() {
    try {
      const picked = await searchAddress()
      if (!picked) return
      setForm((old) => ({ ...old, shipPostalCode: picked.postalCode, shipAddress1: picked.address }))
      setErrors((old) => ({ ...old, shipPostalCode: undefined, shipAddress1: undefined }))
      document.getElementById('shipAddress2')?.focus()
    } catch (e) {
      setError((e as Error).message)
    }
  }
  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (busy) return
    setError('')
    const issues: typeof errors = validate(form)
    if (!agreed) issues.agree = '주문 내용 확인 및 동의가 필요합니다.'
    setErrors(issues)
    if (Object.keys(issues).length) {
      document.getElementById(Object.keys(issues)[0])?.focus()
      return
    }
    if (invalid) {
      setError('일부 옵션의 재고가 부족하거나 판매가 중단되었습니다. 장바구니를 확인해주세요.')
      return
    }
    setBusy(true)
    try {
      const order =
        created ??
        (await createOrder({
          ...form,
          buyerName: form.buyerName.trim(),
          shipName: form.shipName.trim(),
          shipMemo: form.shipMemo.trim(),
          // 상세주소는 선택 항목: 비어 있으면 보내지 않음 (API는 빈 문자열을 거부)
          shipAddress2: form.shipAddress2.trim() || undefined,
          items: items.map((i) => ({ variantId: i.variantId, quantity: i.quantity })),
          paymentMethod: 'BANK_TRANSFER',
        }))
      setCreated(order)
      useCartStore.getState().clearCart()
      router.replace('/order-result?orderId=' + order.id)
    } catch (e) {
      if (e instanceof ApiError && e.fields) {
        const mapped = Object.fromEntries(
          Object.entries(e.fields).filter(([key]) => key in initial),
        ) as typeof errors
        if (Object.keys(mapped).length) setErrors(mapped)
      }
      setError((e as Error).message)
      setBusy(false)
    }
  }

  const field = (
    key: keyof Form,
    label: string,
    options: {
      autoComplete?: string
      tel?: boolean
      optional?: boolean
      maxLength?: number
      placeholder?: string
      readOnly?: boolean
    } = {},
  ) => (
    <label className="field">
      <span>
        {label}
        {options.optional && <span className="font-normal text-muted-foreground"> (선택)</span>}
      </span>
      <input
        id={key}
        className="control"
        type={options.tel ? 'tel' : 'text'}
        inputMode={options.tel ? 'tel' : undefined}
        autoComplete={options.autoComplete}
        maxLength={options.maxLength ?? 100}
        placeholder={options.placeholder}
        required={!options.optional}
        readOnly={options.readOnly}
        aria-invalid={Boolean(errors[key])}
        aria-describedby={errors[key] ? key + '-error' : undefined}
        value={form[key]}
        onChange={(event) => set(key, event.target.value)}
      />
      {errors[key] && (
        <span id={key + '-error'} className="field-error">
          {errors[key]}
        </span>
      )}
    </label>
  )
  const bank = site.data?.bankInstructions

  return (
    <div className="page pb-0 lg:pb-24">
      <PageHeader title="주문 / 결제" back={{ href: '/cart', label: '장바구니' }} />
      <form
        noValidate
        onSubmit={submit}
        className="grid items-start gap-6 lg:grid-cols-[1fr_380px] lg:gap-8"
      >
        <div className="flex flex-col gap-5">
          <fieldset disabled={locked} className="card card-body flex flex-col gap-5">
            <legend className="sr-only">주문자 정보</legend>
            <h2 className="section-title">주문자 정보</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              {field('buyerName', '이름', { autoComplete: 'name' })}
              {field('buyerPhone', '휴대폰 번호', {
                autoComplete: 'tel',
                tel: true,
                placeholder: '010-1234-5678',
              })}
            </div>
          </fieldset>

          <fieldset disabled={locked} className="card card-body flex flex-col gap-5">
            <legend className="sr-only">배송 정보</legend>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="section-title">배송 정보</h2>
              <label className="flex min-h-11 items-center gap-2 text-sm font-medium">
                <input
                  type="checkbox"
                  className="size-[18px]"
                  checked={sameAsBuyer}
                  onChange={(e) => {
                    setSameAsBuyer(e.target.checked)
                    if (e.target.checked)
                      setForm((old) => ({ ...old, shipName: old.buyerName, shipPhone: old.buyerPhone }))
                  }}
                />
                주문자와 같음
              </label>
            </div>
            {!sameAsBuyer && (
              <div className="grid gap-4 sm:grid-cols-2">
                {field('shipName', '받는 분', { autoComplete: 'shipping name' })}
                {field('shipPhone', '받는 분 휴대폰 번호', {
                  autoComplete: 'shipping tel',
                  tel: true,
                  placeholder: '010-1234-5678',
                })}
              </div>
            )}
            <div className="flex flex-col gap-2">
              <label className="field" htmlFor="shipPostalCode">
                우편번호
              </label>
              <div className="flex gap-2">
                <input
                  id="shipPostalCode"
                  className="control w-36"
                  inputMode="numeric"
                  autoComplete="shipping postal-code"
                  maxLength={5}
                  required
                  aria-invalid={Boolean(errors.shipPostalCode)}
                  aria-describedby={errors.shipPostalCode ? 'shipPostalCode-error' : undefined}
                  value={form.shipPostalCode}
                  onChange={(e) => set('shipPostalCode', e.target.value.replace(/\D/g, ''))}
                />
                <button type="button" className="button secondary" onClick={() => void findAddress()}>
                  <MapPin className="size-4" aria-hidden="true" />
                  주소 찾기
                </button>
              </div>
              {errors.shipPostalCode && (
                <span id="shipPostalCode-error" className="field-error mt-0">
                  {errors.shipPostalCode}
                </span>
              )}
              {field('shipAddress1', '주소', { autoComplete: 'shipping address-line1' })}
              {field('shipAddress2', '상세주소', {
                autoComplete: 'shipping address-line2',
                optional: true,
                placeholder: '동·호수 등',
              })}
            </div>
            {field('shipMemo', '배송 메모', {
              optional: true,
              maxLength: 200,
              placeholder: '예: 문 앞에 놓아주세요',
            })}
          </fieldset>

          <section className="card card-body flex flex-col gap-3" aria-labelledby="payment-title">
            <h2 id="payment-title" className="section-title flex items-center gap-2">
              <Landmark className="size-5 text-primary" aria-hidden="true" />
              결제 수단 · 무통장 입금
            </h2>
            {site.loading ? (
              <div className="skeleton h-12" />
            ) : (
              <p className="notice-box whitespace-pre-wrap text-[15px] font-medium">
                {bank || '입금 계좌는 주문 접수 후 안내해드립니다.'}
              </p>
            )}
            <p className="text-sm leading-relaxed text-muted-foreground">
              입금자명을 주문자 이름과 같게 해주세요. 입금이 확인되면 제작·발송을 시작합니다.
            </p>
            <p className="text-sm leading-relaxed text-muted-foreground">
              주문한 날로부터 {site.data?.paymentDueDays ?? 2}일 뒤 23:59까지 입금해주세요. 기한이 지나면 주문이
              자동으로 취소됩니다.
            </p>
          </section>
        </div>

        <aside className="card card-body flex flex-col gap-4 lg:sticky lg:top-[calc(var(--header-height)+24px)]">
          <h2 className="section-title">주문 상품</h2>
          <ul className="flex flex-col gap-3">
            {lines.map((i) => (
              <li key={i.variantId} className="flex items-center gap-3">
                <div className="relative size-14 shrink-0 overflow-hidden rounded-lg bg-muted">
                  {i.product?.images[0] && (
                    <Image
                      fill
                      sizes="56px"
                      src={storageUrl(i.product.images[0])}
                      alt=""
                      className="object-cover"
                    />
                  )}
                </div>
                <div className="min-w-0 flex-1 text-sm">
                  <p className="truncate font-semibold">{i.product?.name}</p>
                  <p className="text-muted-foreground">
                    {i.variant?.name ?? '판매 중단'} · {i.quantity}개
                  </p>
                  {i.variant && i.variant.stock < i.quantity && (
                    <p className="text-destructive">재고 부족</p>
                  )}
                </div>
                <span className="text-sm font-semibold">
                  {formatPrice((i.variant?.priceAmount ?? 0) * i.quantity)}
                </span>
              </li>
            ))}
          </ul>
          <dl className="grid grid-cols-[1fr_auto] gap-y-2 border-t border-border pt-4 text-sm">
            <dt className="text-muted-foreground">상품 금액</dt>
            <dd>{formatPrice(total)}</dd>
            <dt className="text-muted-foreground">배송비</dt>
            <dd>무료</dd>
            <dt className="pt-2 text-base font-bold">입금할 금액</dt>
            <dd className="pt-2 text-xl font-bold">{formatPrice(total)}</dd>
          </dl>
          <div>
            <label className="flex items-start gap-2.5 text-sm leading-relaxed">
              <input
                id="agree"
                type="checkbox"
                className="mt-0.5 size-[18px] shrink-0"
                checked={agreed}
                disabled={locked}
                aria-invalid={Boolean(errors.agree)}
                onChange={(e) => {
                  setAgreed(e.target.checked)
                  setErrors((old) => ({ ...old, agree: undefined }))
                }}
              />
              <span>
                주문 내용을 확인했으며{' '}
                <Link href="/terms" target="_blank" className="link font-medium underline">
                  구매 조건
                </Link>{' '}
                및{' '}
                <Link href="/privacy" target="_blank" className="link font-medium underline">
                  개인정보 수집·이용
                </Link>
                에 동의합니다 (필수)
              </span>
            </label>
            {errors.agree && <span className="field-error">{errors.agree}</span>}
          </div>
          {invalid && (
            <p className="notice-box">
              재고가 부족한 상품이 있습니다.{' '}
              <Link href="/cart" className="link">
                장바구니에서 수정
              </Link>
            </p>
          )}
          {error && (
            <p role="alert" className="field-error mt-0">
              {error}
            </p>
          )}
          <button
            className="button lg hidden lg:inline-flex"
            disabled={busy || invalid || !agreed}
            aria-busy={busy}
          >
            {busy ? '주문 접수 중…' : formatPrice(total) + ' 주문하기'}
          </button>
        </aside>

        <div className="sticky bottom-0 z-40 -mx-5 mt-8 border-t border-border bg-background/95 px-5 md:-mx-8 md:px-8 pb-[calc(12px+env(safe-area-inset-bottom))] pt-3 backdrop-blur lg:hidden">
          <button className="button lg w-full" disabled={busy || invalid || !agreed} aria-busy={busy}>
            {busy ? '주문 접수 중…' : formatPrice(total) + ' 주문하기'}
          </button>
        </div>
      </form>
    </div>
  )
}
