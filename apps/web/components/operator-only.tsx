'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { AlertCircle, Lock } from 'lucide-react'
import { useAuthStore } from '@/lib/store'
import { EmptyState, PageLoading } from './ui-kit'

/** 운영자만 볼 수 있는 화면 감싸기 */
export function OperatorOnly({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user),
    ready = useAuthStore((s) => s.ready),
    path = usePathname()
  if (!ready) return <PageLoading label="계정 확인 중" />
  if (!user)
    return (
      <div className="page page-narrow">
        <EmptyState
          icon={<Lock className="size-5" />}
          title="로그인이 필요합니다"
          description="운영자 계정으로 로그인한 뒤 이용해주세요."
          action={
            <Link className="button" href={'/login?next=' + encodeURIComponent(path)}>
              로그인
            </Link>
          }
        />
      </div>
    )
  if (user.role !== 'ADMIN')
    return (
      <div className="page page-narrow" role="alert">
        <EmptyState
          icon={<Lock className="size-5" />}
          title="운영자 권한이 필요합니다"
          description="이 화면은 운영자만 이용할 수 있습니다."
          action={
            <Link className="button secondary" href="/">
              홈으로
            </Link>
          }
        />
      </div>
    )
  return children
}

/** 로그인한 회원만 볼 수 있는 화면 감싸기 */
export function MemberOnly({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user),
    ready = useAuthStore((s) => s.ready),
    path = usePathname()
  if (!ready) return <PageLoading label="계정 확인 중" />
  if (!user)
    return (
      <div className="page page-narrow">
        <EmptyState
          icon={<Lock className="size-5" />}
          title="로그인이 필요합니다"
          description="로그인하면 주문 내역과 계정 정보를 볼 수 있습니다."
          action={
            <Link className="button" href={'/login?next=' + encodeURIComponent(path)}>
              로그인
            </Link>
          }
        />
      </div>
    )
  return children
}

export function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="card my-6 flex flex-col items-start gap-3 p-5 sm:flex-row sm:items-center"
    >
      <AlertCircle className="size-5 shrink-0 text-destructive" aria-hidden="true" />
      <p className="flex-1 text-sm">{message}</p>
      <button className="button secondary sm" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  )
}
