'use client'

import Link from 'next/link'
import Image from 'next/image'
import { ArrowRight, Landmark, RotateCcw, Truck } from 'lucide-react'
import { mapApiProduct, storageUrl, type ApiProduct } from '@/lib/api'
import { useApi } from '@/hooks/use-api'
import { LoadError } from '@/components/operator-only'
import { ProductCard } from '@/components/product-card'
import { ProductGridSkeleton } from '@/components/ui-kit'

function Hero() {
  return (
    <section className="mx-auto max-w-[1264px] px-4 pt-6 md:px-8 md:pt-10">
      <div className="grid overflow-hidden rounded-3xl bg-secondary md:grid-cols-[5fr_7fr]">
        <div className="flex flex-col justify-center gap-5 px-6 py-10 md:px-14 md:py-16">
          <p className="eyebrow">손으로 깎는 생활 목공</p>
          <h1 className="text-[34px] font-bold leading-[1.2] tracking-[-0.03em] md:text-5xl">
            매일 손에 닿는
            <br />
            나무 살림
          </h1>
          <p className="max-w-md text-base leading-relaxed text-foreground/80 md:text-[17px]">
            도마·트레이·수납함처럼 매일 쓰는 물건을 원목으로 만듭니다. 결과 색은 하나하나
            다르게 남습니다.
          </p>
          <div className="mt-1 flex flex-wrap gap-2.5">
            <Link href="/products" className="button lg">
              상품 둘러보기
            </Link>
            <Link href="/posts" className="button secondary lg">
              작업 이야기
            </Link>
          </div>
        </div>
        <div className="relative min-h-[260px] md:min-h-[520px]">
          <Image
            unoptimized
            fill
            priority
            sizes="(max-width: 768px) 100vw, 60vw"
            src="/images/hero-workshop.jpg"
            alt="나무 작업대와 공구가 놓인 담담공방 작업실"
            className="object-cover"
          />
        </div>
      </div>
    </section>
  )
}

function CategoryTile({ category }: { category: string }) {
  const result = useApi<{ products: ApiProduct[] }>(
    '/products?limit=1&category=' + encodeURIComponent(category),
  )
  const image = result.data?.products[0]?.images
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)[0]?.objectKey
  return (
    <Link
      href={'/products?category=' + encodeURIComponent(category)}
      className="group relative block aspect-[4/3] overflow-hidden rounded-2xl bg-muted"
    >
      {image ? (
        <Image
          unoptimized
          fill
          sizes="(max-width: 768px) 50vw, 25vw"
          src={storageUrl(image)}
          alt=""
          className="object-cover transition-transform duration-500 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100"
        />
      ) : (
        result.loading && <span className="skeleton absolute inset-0 rounded-none" />
      )}
      <span className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-black/55 to-transparent" />
      <span className="absolute bottom-3.5 left-4 flex items-center gap-1 text-[17px] font-bold text-white">
        {category}
        <ArrowRight className="size-4 opacity-0 transition-opacity group-hover:opacity-100" />
      </span>
    </Link>
  )
}

function Categories() {
  const result = useApi<string[]>('/products/categories')
  const categories = (result.data ?? []).slice(0, 6)
  if (result.error || (!result.loading && categories.length === 0)) return null
  return (
    <section className="mx-auto max-w-[1264px] px-4 pt-16 md:px-8 md:pt-20">
      <h2 className="mb-6 text-2xl font-bold">분류별로 보기</h2>
      <div
        className={`grid grid-cols-2 gap-3 md:gap-4 ${
          categories.length > 4 ? 'md:grid-cols-3' : 'md:grid-cols-4'
        }`}
      >
        {result.loading
          ? Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="skeleton aspect-[4/3] rounded-2xl" />
            ))
          : categories.map((category) => <CategoryTile key={category} category={category} />)}
      </div>
    </section>
  )
}

function NewArrivals() {
  const result = useApi<{ products: ApiProduct[] }>('/products?limit=8&sort=newest')
  const products = (result.data?.products ?? []).map(mapApiProduct)
  return (
    <section className="mx-auto max-w-[1264px] px-4 pt-16 md:px-8 md:pt-20">
      <div className="mb-6 flex items-end justify-between gap-4">
        <h2 className="text-2xl font-bold">새로 나온 물건</h2>
        <Link href="/products" className="link inline-flex items-center gap-1 text-[15px]">
          전체 보기
          <ArrowRight className="size-4" aria-hidden="true" />
        </Link>
      </div>
      {result.error ? (
        <LoadError message={result.error} onRetry={result.reload} />
      ) : result.loading ? (
        <ProductGridSkeleton count={8} />
      ) : products.length === 0 ? (
        <p className="card px-6 py-12 text-center text-muted-foreground">
          준비 중인 상품이 곧 올라옵니다.
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4 lg:gap-x-6">
          {products.map((product, index) => (
            <ProductCard key={product.id} product={product} priority={index < 4} />
          ))}
        </div>
      )}
    </section>
  )
}

function Story() {
  return (
    <section className="mx-auto max-w-[1264px] px-4 pt-20 md:px-8 md:pt-24">
      <div className="grid items-center gap-8 md:grid-cols-2 md:gap-12">
        <div className="relative aspect-[4/3] overflow-hidden rounded-2xl bg-muted">
          <Image
            unoptimized
            fill
            sizes="(max-width: 768px) 100vw, 50vw"
            src="/images/product-11.jpg"
            alt="오일로 마감한 원목 서빙 보드"
            className="object-cover"
          />
        </div>
        <div className="flex flex-col gap-4">
          <p className="eyebrow">만드는 방식</p>
          <h2 className="text-[28px] font-bold leading-snug md:text-[32px]">
            고르고, 깎고,
            <br />
            오일로 마감합니다
          </h2>
          <p className="max-w-lg leading-relaxed text-foreground/80">
            나무를 고르고 재단한 뒤 대패와 사포로 표면을 정리합니다. 식물성 오일로 마감해 손에
            닿는 감촉을 부드럽게 남깁니다. 작업 과정은 작업 이야기에 기록합니다.
          </p>
          <Link href="/posts" className="link inline-flex items-center gap-1">
            작업 이야기 읽기
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </section>
  )
}

const services = [
  {
    icon: Landmark,
    title: '무통장 입금으로 주문',
    body: '주문 후 안내 계좌로 입금하시면 확인 뒤 제작·발송합니다.',
  },
  { icon: Truck, title: '배송비 무료', body: '모든 주문을 배송비 없이 보내드립니다.' },
  {
    icon: RotateCcw,
    title: '수령 후 7일 안 교환·반품',
    body: '자세한 기준은 교환·반품 안내에서 확인하세요.',
    href: '/exchange-policy',
  },
]

function Services() {
  return (
    <section className="mx-auto max-w-[1264px] px-4 pb-20 pt-20 md:px-8 md:pb-24 md:pt-24">
      <div className="grid gap-3 md:grid-cols-3 md:gap-4">
        {services.map(({ icon: Icon, title, body, href }) => (
          <div key={title} className="card flex gap-4 p-6">
            <Icon className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" />
            <div className="flex flex-col gap-1.5">
              <p className="font-bold">{title}</p>
              <p className="text-sm leading-relaxed text-muted-foreground">{body}</p>
              {href && (
                <Link href={href} className="link text-sm">
                  교환·반품 안내
                </Link>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

export default function HomePage() {
  return (
    <>
      <Hero />
      <Categories />
      <NewArrivals />
      <Story />
      <Services />
    </>
  )
}
