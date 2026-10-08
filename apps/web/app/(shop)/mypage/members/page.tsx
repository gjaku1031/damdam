'use client'
import { useEffect, useState } from 'react'
import { Search, Users } from 'lucide-react'
import { toast } from 'sonner'
import { OperatorOnly, LoadError } from '@/components/operator-only'
import { AdminHeader, formatDateTime } from '@/components/admin-nav'
import { confirmAction } from '@/components/confirm-dialog'
import { EmptyState, ListSkeleton, Pagination } from '@/components/ui-kit'
import { useApi } from '@/hooks/use-api'
import { mutate, type ApiUser } from '@/lib/api'

const PAGE_SIZE = 50

export default function Page() {
  return (
    <OperatorOnly>
      <Members />
    </OperatorOnly>
  )
}

function Members() {
  const [page, setPage] = useState(1),
    [term, setTerm] = useState(''),
    [search, setSearch] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(term.trim())
      setPage(1)
    }, 300)
    return () => clearTimeout(timer)
  }, [term])
  const data = useApi<{ users: ApiUser[]; total: number }>(
      `/admin/users?limit=${PAGE_SIZE}&page=${page}&search=${encodeURIComponent(search)}`,
    ),
    [pending, setPending] = useState<string | null>(null)

  async function block(user: ApiUser) {
    const blocking = !user.communityBlockedAt
    const ok = await confirmAction({
      title: blocking ? `${user.username}님의 댓글을 제한할까요?` : `${user.username}님의 댓글 제한을 풀까요?`,
      description: blocking
        ? '제한하면 새 댓글을 쓸 수 없습니다. 이미 쓴 댓글은 그대로 남습니다.'
        : '다시 댓글을 쓸 수 있게 됩니다.',
      confirmLabel: blocking ? '댓글 제한' : '제한 해제',
      tone: blocking ? 'danger' : 'default',
    })
    if (!ok) return
    setPending(user.id)
    try {
      await mutate('/admin/users/' + user.id + (blocking ? '/block' : '/unblock'), 'POST')
      toast.success(blocking ? '댓글을 제한했습니다.' : '댓글 제한을 풀었습니다.')
      data.reload()
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setPending(null)
    }
  }

  return (
    <div className="page">
      <AdminHeader
        title="회원 관리"
        description={data.data ? `전체 ${data.data.total}명` : '가입한 회원을 확인하고 댓글을 제한할 수 있습니다.'}
      />
      <label className="relative mb-5 block max-w-md">
        <span className="sr-only">회원 아이디 검색</span>
        <Search
          className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-subtle-foreground"
          aria-hidden="true"
        />
        <input
          className="control pl-10"
          type="search"
          placeholder="아이디로 검색"
          value={term}
          onChange={(e) => setTerm(e.target.value)}
        />
      </label>
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {data.loading && <ListSkeleton rows={4} />}
      {data.data && data.data.users.length === 0 && (
        <EmptyState icon={<Users className="size-5" />} title="해당하는 회원이 없습니다" />
      )}
      {data.data && data.data.users.length > 0 && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead className="border-b border-border bg-muted/50 text-[13px] text-muted-foreground">
              <tr>
                <th scope="col" className="px-5 py-3 font-semibold">아이디</th>
                <th scope="col" className="px-5 py-3 font-semibold">가입일</th>
                <th scope="col" className="px-5 py-3 font-semibold">댓글</th>
                <th scope="col" className="px-5 py-3 text-right font-semibold">
                  <span className="sr-only">관리</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.data.users.map((user) => (
                <tr key={user.id} className="border-b border-border last:border-b-0">
                  <td className="px-5 py-3.5 font-medium">
                    <span className="flex items-center gap-2">
                      {user.username}
                      {user.role === 'ADMIN' && <span className="badge badge-brand">운영자</span>}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-muted-foreground">{formatDateTime(user.createdAt, false)}</td>
                  <td className="px-5 py-3.5">
                    {user.communityBlockedAt ? (
                      <span className="badge badge-danger">제한됨</span>
                    ) : (
                      <span className="badge">정상</span>
                    )}
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    {user.role === 'CUSTOMER' && (
                      <button
                        type="button"
                        className={user.communityBlockedAt ? 'button secondary sm' : 'button danger sm'}
                        disabled={pending === user.id}
                        onClick={() => void block(user)}
                      >
                        {user.communityBlockedAt ? '제한 해제' : '댓글 제한'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data.data && (
        <Pagination page={page} total={data.data.total} size={PAGE_SIZE} onChange={setPage} label="회원 페이지" />
      )}
    </div>
  )
}
