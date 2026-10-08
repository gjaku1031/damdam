'use client'
import Link from 'next/link'
import { EmptyState } from '@/components/ui-kit'
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <div className="page page-narrow" role="alert">
      <EmptyState
        title="글을 불러오지 못했습니다"
        description="연결 상태를 확인하고 다시 시도해주세요."
        action={
          <div className="flex flex-wrap justify-center gap-2">
            <button type="button" className="button" onClick={reset}>
              다시 시도
            </button>
            <Link href="/posts" className="button secondary">
              작업 이야기 목록
            </Link>
          </div>
        }
      />
    </div>
  )
}
