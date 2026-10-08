'use client'
import { Suspense, useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { PackageSearch, Plus, Search } from 'lucide-react'
import { toast } from 'sonner'
import { useApi } from '@/hooks/use-api'
import { useAuthStore } from '@/lib/store'
import { mapApiProduct, mutate, type ApiProduct, type ProductSort } from '@/lib/api'
import { ProductEditor } from '@/components/product-editor'
import { LoadError } from '@/components/operator-only'
import { AppSelect } from '@/components/app-select'
import { ProductCard } from '@/components/product-card'
import { confirmAction } from '@/components/confirm-dialog'
import { EmptyState, PageHeader, Pagination, ProductGridSkeleton } from '@/components/ui-kit'

const PAGE_SIZE = 12
const sorts: { value: ProductSort; label: string }[] = [
  { value: 'newest', label: '최신순' },
  { value: 'price_asc', label: '낮은 가격순' },
  { value: 'price_desc', label: '높은 가격순' },
  { value: 'name', label: '이름순' },
]

export default function Page() {
  return (
    <Suspense
      fallback={
        <div className="page">
          <ProductGridSkeleton />
        </div>
      }
    >
      <Products />
    </Suspense>
  )
}

function Products() {
  const params = useSearchParams(),
    router = useRouter()
  const query = params.get('q') ?? '',
    category = params.get('category') ?? '',
    sortParam = params.get('sort') ?? 'newest',
    sort: ProductSort = sorts.some((s) => s.value === sortParam)
      ? (sortParam as ProductSort)
      : 'newest',
    page = Math.max(1, Number(params.get('page')) || 1)
  const edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN')
  const [search, setSearch] = useState(query),
    [editor, setEditor] = useState<{ product: ApiProduct | null } | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const categories = useApi<string[]>('/products/categories')
  const data = useApi<{ products: ApiProduct[]; total: number }>(
    (edit ? '/admin/products' : '/products') +
      '?limit=' +
      PAGE_SIZE +
      '&page=' +
      page +
      '&sort=' +
      sort +
      '&search=' +
      encodeURIComponent(query) +
      (category ? '&category=' + encodeURIComponent(category) : ''),
  )
  useEffect(() => setSearch(query), [query])
  useEffect(() => {
    if (params.has('search')) input.current?.focus()
  }, [params])

  function href(next: { q?: string; category?: string; sort?: string; page?: number }) {
    const merged = { q: query, category, sort, page: 1, ...next }
    const out = new URLSearchParams()
    if (merged.q) out.set('q', merged.q)
    if (merged.category) out.set('category', merged.category)
    if (merged.sort && merged.sort !== 'newest') out.set('sort', merged.sort)
    if (merged.page > 1) out.set('page', String(merged.page))
    const qs = out.toString()
    return '/products' + (qs ? '?' + qs : '')
  }
  async function remove(product: ApiProduct) {
    const ok = await confirmAction({
      title: `‘${product.name}’ 상품을 삭제할까요?`,
      description: '상품 목록에서 사라집니다. 이미 받은 주문 기록은 그대로 남습니다.',
      confirmLabel: '삭제',
      tone: 'danger',
    })
    if (!ok) return
    try {
      await mutate('/admin/products/' + product.id, 'DELETE')
      toast.success('상품을 삭제했습니다.')
      data.reload()
    } catch (e) {
      toast.error((e as Error).message)
    }
  }
  const filtered = Boolean(query || category)
  const chip = (value: string, label: string) => {
    const active = category === value
    return (
      <Link
        key={value || 'all'}
        href={href({ category: value })}
        scroll={false}
        aria-current={active ? 'page' : undefined}
        className={`inline-flex min-h-10 items-center rounded-full border px-4 text-sm font-semibold transition-colors ${
          active
            ? 'border-primary bg-primary text-primary-foreground'
            : 'border-border-strong bg-card hover:border-muted-foreground'
        }`}
      >
        {label}
      </Link>
    )
  }

  return (
    <div className="page">
      <PageHeader
        title={category || '전체 상품'}
        description="손으로 깎고 오일로 마감한 원목 살림"
        actions={
          edit && (
            <button className="button" onClick={() => setEditor({ product: null })}>
              <Plus className="size-4" aria-hidden="true" />
              상품 추가
            </button>
          )
        }
      />
      <div className="mb-8 flex flex-col gap-4">
        <form
          role="search"
          className="flex max-w-xl gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            router.push(href({ q: search.trim() }), { scroll: false })
          }}
        >
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <input
              id="product-search"
              ref={input}
              type="search"
              aria-label="상품명으로 검색"
              className="control pl-10"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="상품명으로 검색"
            />
          </div>
          <button className="button secondary">검색</button>
        </form>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <nav aria-label="상품 분류" className="flex flex-wrap gap-2">
            {chip('', '전체')}
            {(categories.data ?? []).map((c) => chip(c, c))}
          </nav>
          <div className="flex items-center gap-3">
            {data.data && (
              <span className="text-sm text-muted-foreground">{data.data.total}개</span>
            )}
            <AppSelect
              label="정렬"
              className="w-auto min-w-36"
              value={sort}
              onValueChange={(value) => router.push(href({ sort: value }), { scroll: false })}
              options={sorts}
            />
          </div>
        </div>
      </div>

      {data.error ? (
        <LoadError message={data.error} onRetry={data.reload} />
      ) : data.loading ? (
        <ProductGridSkeleton count={PAGE_SIZE} />
      ) : data.data?.products.length === 0 ? (
        <EmptyState
          icon={<PackageSearch className="size-5" />}
          title={filtered ? '조건에 맞는 상품이 없습니다' : '등록된 상품이 없습니다'}
          description={filtered ? '검색어나 분류를 바꿔 다시 찾아보세요.' : undefined}
          action={
            filtered && (
              <Link href="/products" className="button secondary">
                전체 상품 보기
              </Link>
            )
          }
        />
      ) : (
        <div className="grid grid-cols-2 gap-x-4 gap-y-10 md:grid-cols-3 lg:grid-cols-4 lg:gap-x-6">
          {data.data?.products.map((raw, index) => (
            <div key={raw.id} className="flex flex-col gap-3">
              <ProductCard
                product={mapApiProduct(raw)}
                priority={index < 4}
                hidden={!raw.visible}
              />
              {edit && (
                <div className="flex gap-2">
                  <button
                    className="button secondary sm flex-1"
                    onClick={() => setEditor({ product: raw })}
                  >
                    수정
                  </button>
                  <button className="button danger sm flex-1" onClick={() => void remove(raw)}>
                    삭제
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {data.data && (
        <Pagination
          page={page}
          total={data.data.total}
          size={PAGE_SIZE}
          hrefFor={(p) => href({ page: p })}
          label="상품 페이지"
        />
      )}
      <ProductEditor
        open={Boolean(editor)}
        product={editor?.product ?? null}
        onClose={() => setEditor(null)}
        onSaved={() => {
          data.reload()
          categories.reload()
        }}
      />
    </div>
  )
}
