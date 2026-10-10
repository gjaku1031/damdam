import Link from 'next/link'
import Image from 'next/image'
import { storageUrl, type Product } from '@/lib/api'
import { formatPrice } from '@/lib/data'

/** 상품 목록·홈에서 같이 쓰는 상품 카드 */
export function ProductCard({
  product,
  priority = false,
  hidden = false,
}: {
  product: Product
  priority?: boolean
  hidden?: boolean
}) {
  const soldOut = product.variants.length > 0 && product.variants.every((v) => v.stock <= 0)
  return (
    <Link href={'/products/' + product.id} className="group flex flex-col gap-2.5">
      <div className="relative aspect-square overflow-hidden rounded-xl bg-muted">
        {product.images[0] && (
          <Image
            fill
            priority={priority}
            sizes="(max-width: 768px) 50vw, (max-width: 1024px) 33vw, 25vw"
            src={storageUrl(product.images[0])}
            alt={product.name}
            className="object-cover transition-transform duration-500 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100"
          />
        )}
        <div className="absolute left-2.5 top-2.5 flex gap-1.5">
          {soldOut && <span className="badge bg-foreground text-background">품절</span>}
          {hidden && <span className="badge badge-warning">비공개</span>}
        </div>
      </div>
      {product.category && (
        <span className="text-[13px] text-muted-foreground">{product.category}</span>
      )}
      <span className="-mt-1.5 line-clamp-2 text-[15px] font-semibold leading-snug group-hover:text-primary">
        {product.name}
      </span>
      <span className="text-[15px] font-bold">{formatPrice(product.price)}</span>
    </Link>
  )
}
