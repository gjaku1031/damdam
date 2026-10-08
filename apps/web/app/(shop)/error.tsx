'use client'
import Link from 'next/link'
import { EmptyState } from '@/components/ui-kit'

export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <div className="page page-narrow" role="alert">
      <EmptyState
        title="화면을 불러오지 못했습니다"
        description="잠시 후 다시 시도해주세요. 문제가 계속되면 고객센터로 알려주세요."
        action={
          <div className="flex flex-wrap justify-center gap-2">
            <button type="button" className="button" onClick={reset}>
              다시 시도
            </button>
            <Link href="/" className="button secondary">
              홈으로
            </Link>
          </div>
        }
      />
    </div>
  )
}
