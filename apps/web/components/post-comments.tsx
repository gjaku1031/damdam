'use client'
import { useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { MessageCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useAuthStore } from '@/lib/store'
import { useApi } from '@/hooks/use-api'
import { mutate, type PostComment } from '@/lib/api'
import { formatDateTime } from '@/lib/format-date'
import { LoadError } from './operator-only'
import { confirmAction } from './confirm-dialog'

const MAX_LENGTH = 2000

export function PostComments({ id }: { id: string }) {
  const user = useAuthStore((s) => s.user),
    edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN'),
    path = usePathname()
  const result = useApi<PostComment[]>((edit ? '/admin/posts/' : '/posts/') + id + '/comments')
  const [content, setContent] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  async function add() {
    setBusy(true)
    setError('')
    try {
      await mutate('/posts/' + id + '/comments', 'POST', { content })
      setContent('')
      toast.success('댓글을 남겼습니다.')
      result.reload()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }
  async function remove(comment: PostComment, moderate: boolean) {
    const ok = await confirmAction(
      moderate
        ? {
            title: '이 댓글을 숨길까요?',
            description: '방문자에게 더 이상 보이지 않습니다.',
            confirmLabel: '숨기기',
            tone: 'danger',
          }
        : {
            title: '댓글을 삭제할까요?',
            description: '삭제한 댓글은 되돌릴 수 없습니다.',
            confirmLabel: '삭제',
            tone: 'danger',
          },
    )
    if (!ok) return
    try {
      await mutate(
        (moderate ? '/admin/posts/' : '/posts/') + id + '/comments/' + comment.id,
        'DELETE',
      )
      toast.success(moderate ? '댓글을 숨겼습니다.' : '댓글을 삭제했습니다.')
      result.reload()
    } catch (e) {
      setError((e as Error).message)
    }
  }
  const comments = result.data ?? []
  return (
    <section id="comments" aria-labelledby="comments-title" className="mt-14 border-t border-border pt-10">
      <h2 id="comments-title" className="section-title mb-6">
        댓글 <span className="text-muted-foreground">{result.data ? comments.length : ''}</span>
      </h2>
      {result.error && <LoadError message={result.error} onRetry={result.reload} />}
      {result.loading && !result.data && (
        <div className="flex flex-col gap-3" aria-hidden="true">
          <div className="skeleton h-16" />
          <div className="skeleton h-16" />
        </div>
      )}
      {result.data && comments.length === 0 && (
        <p className="flex items-center gap-2 rounded-xl bg-muted px-4 py-5 text-sm text-muted-foreground">
          <MessageCircle className="size-4" aria-hidden="true" />
          아직 댓글이 없습니다. 첫 댓글을 남겨주세요.
        </p>
      )}
      {comments.length > 0 && (
        <ul className="divide-y divide-border">
          {comments.map((comment) => (
            <li key={comment.id} className="py-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-semibold">{comment.user?.username ?? '회원'}</span>
                  <time className="text-[13px] text-muted-foreground" dateTime={comment.createdAt}>
                    {formatDateTime(comment.createdAt)}
                  </time>
                  {comment.hiddenAt && <span className="badge">숨김</span>}
                </p>
                <div className="flex gap-1">
                  {user?.id === comment.userId && !comment.hiddenAt && (
                    <button className="button ghost sm" onClick={() => void remove(comment, false)}>
                      삭제
                    </button>
                  )}
                  {edit && !comment.hiddenAt && (
                    <button
                      className="button ghost sm text-destructive"
                      onClick={() => void remove(comment, true)}
                    >
                      숨기기
                    </button>
                  )}
                </div>
              </div>
              <p
                className={`mt-2 whitespace-pre-wrap break-words text-[15px] leading-relaxed ${
                  comment.hiddenAt ? 'text-muted-foreground' : ''
                }`}
              >
                {comment.content}
              </p>
            </li>
          ))}
        </ul>
      )}
      {user ? (
        <form
          className="mt-6"
          onSubmit={(e) => {
            e.preventDefault()
            void add()
          }}
        >
          <label htmlFor="comment-content" className="sr-only">
            댓글 내용
          </label>
          <textarea
            id="comment-content"
            className="control min-h-28 resize-y"
            placeholder="댓글을 남겨주세요"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            maxLength={MAX_LENGTH}
            required
            disabled={busy}
          />
          <div className="mt-3 flex items-center justify-between gap-3">
            <span className="text-[13px] text-muted-foreground" aria-live="polite">
              {content.length.toLocaleString('ko-KR')}/{MAX_LENGTH.toLocaleString('ko-KR')}
            </span>
            <button className="button" disabled={busy || !content.trim()}>
              {busy ? '등록 중…' : '댓글 등록'}
            </button>
          </div>
        </form>
      ) : (
        <div className="card mt-6 flex flex-col items-start justify-between gap-3 p-5 sm:flex-row sm:items-center">
          <p className="text-sm text-muted-foreground">로그인하면 댓글을 남길 수 있습니다.</p>
          <Link
            className="button secondary"
            href={'/login?next=' + encodeURIComponent(path + '#comments')}
          >
            로그인하고 댓글 남기기
          </Link>
        </div>
      )}
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
    </section>
  )
}
export function PostEditLink({ id }: { id: string }) {
  const edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN')
  return edit ? (
    <Link className="button secondary sm" href={'/posts/' + id + '/edit'}>
      글 수정
    </Link>
  ) : null
}
