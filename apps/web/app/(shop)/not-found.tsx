import Link from 'next/link'
import { EmptyState } from '@/components/ui-kit'

export default function NotFound() {
  return (
    <div className="page page-narrow">
      <EmptyState
        title="페이지를 찾을 수 없습니다"
        description="주소가 바뀌었거나 삭제된 페이지입니다. 주소를 다시 확인해주세요."
        action={
          <div className="flex flex-wrap justify-center gap-2">
            <Link href="/" className="button">
              홈으로
            </Link>
            <Link href="/products" className="button secondary">
              상품 보기
            </Link>
          </div>
        }
      />
    </div>
  )
}
