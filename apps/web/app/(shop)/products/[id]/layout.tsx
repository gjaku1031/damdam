import type { Metadata } from 'next'

/** 공개 상품이면 상품명·설명·대표 사진으로 제목과 공유 미리보기 구성 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>
}): Promise<Metadata> {
  const { id } = await params
  try {
    const response = await fetch(
      (process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4000') + '/api/products/' + encodeURIComponent(id),
      { cache: 'no-store' },
    )
    if (!response.ok) return { title: '상품' }
    const product: {
      name: string
      description: string | null
      images: { objectKey: string; sortOrder: number }[]
    } = await response.json()
    const cover = [...product.images].sort((a, b) => a.sortOrder - b.sortOrder)[0]?.objectKey
    const description = product.description?.slice(0, 120) || undefined
    return {
      // 상위 products 레이아웃의 문자열 제목이 루트 템플릿을 끊어 직접 붙임
      title: { absolute: product.name + ' · 담담공방' },
      description,
      openGraph: {
        title: product.name,
        description,
        images: cover ? ['/api/storage/files/' + cover.replace(/^\//, '')] : undefined,
      },
    }
  } catch {
    return { title: '상품' }
  }
}

export default function Layout({ children }: { children: React.ReactNode }) {
  return children
}
