import Link from 'next/link'
import { EmptyState } from '@/components/ui-kit'
export default function NotFound() {
  return (
    <div className="page page-narrow">
      <EmptyState
        title="글을 찾을 수 없습니다"
        description="삭제되었거나 비공개로 바뀐 글입니다."
        action={
          <Link href="/posts" className="button secondary">
            작업 이야기 목록
          </Link>
        }
      />
    </div>
  )
}
