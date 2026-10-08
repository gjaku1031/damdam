'use client'
import { AppSelect } from './app-select'
import { useState } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { MessageCircle, NotebookPen, PenLine, Search } from 'lucide-react'
import { toast } from 'sonner'
import { useAuthStore } from '@/lib/store'
import { useApi } from '@/hooks/use-api'
import { newPost, storageUrl, mutate, type PostSummary, type Category } from '@/lib/api'
import { LoadError } from './operator-only'
import { EmptyState, ListSkeleton, PageHeader, Pagination } from './ui-kit'
import { confirmAction } from './confirm-dialog'
import { formatDate } from '@/lib/format-date'

const PAGE_SIZE = 20
const stateLabels = { DRAFT: '임시글', PUBLISHED: '공개', PRIVATE: '비공개' } as const

export function PostsList({
  initial,
  categories,
}: {
  initial: { items: PostSummary[]; total: number }
  categories: Category[]
}) {
  const edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN'),
    router = useRouter()
  const [search, setSearch] = useState(''),
    [query, setQuery] = useState(''),
    [category, setCategory] = useState(''),
    [state, setState] = useState(''),
    [page, setPage] = useState(1),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  const result = useApi<{ items: PostSummary[]; total: number }>(
    (edit ? '/admin/posts' : '/posts') +
      '?page=' +
      page +
      '&size=' +
      PAGE_SIZE +
      '&search=' +
      encodeURIComponent(query) +
      (category ? '&category=' + category : '') +
      (edit && state ? '&state=' + state : ''),
  )
  const usingInitial = !edit && !query && !category && page === 1 && !result.error
  const data = result.data ?? (usingInitial ? initial : null)
  async function create() {
    setBusy(true)
    setError('')
    try {
      const post = await newPost()
      router.push('/posts/' + post.id + '/edit')
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }
  async function remove(post: PostSummary) {
    const ok = await confirmAction({
      title: '이 글을 삭제할까요?',
      description: `"${post.title || '제목 없는 임시글'}"을 삭제하면 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      tone: 'danger',
    })
    if (!ok) return
    try {
      await mutate('/admin/posts/' + post.id, 'DELETE')
      toast.success('글을 삭제했습니다.')
      result.reload()
    } catch (e) {
      setError((e as Error).message)
    }
  }
  async function hide(post: PostSummary) {
    const ok = await confirmAction({
      title: '비공개로 바꿀까요?',
      description: '방문자 목록에서 사라지며, 다시 발행하면 공개됩니다.',
      confirmLabel: '비공개로 전환',
    })
    if (!ok) return
    try {
      await mutate('/admin/posts/' + post.id + '/hide', 'POST', { revision: post.revision })
      toast.success('비공개로 바꿨습니다.')
      result.reload()
    } catch (e) {
      setError((e as Error).message)
    }
  }
  const filtered = Boolean(query || category || state)
  const featured = !edit && !filtered && page === 1 ? data?.items[0] : undefined
  const rest = featured ? data!.items.slice(1) : (data?.items ?? [])
  return (
    <div className="page page-medium">
      <PageHeader
        title="작업 이야기"
        description="공방의 작업 과정과 소식"
        actions={
          edit && (
            <button className="button" disabled={busy} onClick={() => void create()}>
              <PenLine className="size-4" aria-hidden="true" />
              {busy ? '준비 중…' : '글쓰기'}
            </button>
          )
        }
      />
      <form
        role="search"
        className="mb-8 flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          setQuery(search.trim())
          setPage(1)
        }}
      >
        <div className="relative min-w-48 flex-1">
          <Search
            className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-subtle-foreground"
            aria-hidden="true"
          />
          <input
            type="search"
            className="control pl-10"
            aria-label="작업 이야기 검색"
            placeholder="제목이나 내용으로 검색"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <AppSelect
          label="분류"
          className="w-auto min-w-36"
          value={category}
          onValueChange={(value) => {
            setCategory(value)
            setPage(1)
          }}
          options={[
            { value: '', label: '전체 분류' },
            ...categories.map((c) => ({ value: c.id, label: c.name })),
          ]}
        />
        {edit && (
          <AppSelect
            label="공개 상태"
            className="w-auto min-w-32"
            value={state}
            onValueChange={(value) => {
              setState(value)
              setPage(1)
            }}
            options={[
              { value: '', label: '전체 상태' },
              ...Object.entries(stateLabels).map(([value, label]) => ({ value, label })),
            ]}
          />
        )}
        <button className="button secondary">검색</button>
      </form>
      {result.error && <LoadError message={result.error} onRetry={result.reload} />}
      {error && (
        <p role="alert" className="field-error mb-4">
          {error}
        </p>
      )}
      {!data && !result.error && <ListSkeleton rows={4} />}
      {data && data.items.length === 0 && (
        <EmptyState
          icon={<NotebookPen className="size-5" />}
          title={filtered ? '조건에 맞는 글이 없습니다' : '아직 올라온 글이 없습니다'}
          description={filtered ? '검색어나 분류를 바꿔 다시 찾아보세요.' : undefined}
          action={
            filtered && (
              <button
                type="button"
                className="button secondary"
                onClick={() => {
                  setSearch('')
                  setQuery('')
                  setCategory('')
                  setState('')
                  setPage(1)
                }}
              >
                조건 초기화
              </button>
            )
          }
        />
      )}
      {featured && <FeaturedPost post={featured} />}
      {rest.length > 0 && (
        <ul className="divide-y divide-border border-y border-border">
          {rest.map((p) => (
            <li key={p.id} className="py-6">
              <PostRow post={p} />
              {edit && (
                <div className="mt-3 flex flex-wrap justify-end gap-1">
                  <Link className="button ghost sm" href={'/posts/' + p.id + '/edit'}>
                    수정
                  </Link>
                  {p.state === 'PUBLISHED' && (
                    <button className="button ghost sm" onClick={() => void hide(p)}>
                      비공개로 전환
                    </button>
                  )}
                  <button
                    className="button ghost sm text-destructive"
                    onClick={() => void remove(p)}
                  >
                    삭제
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {data && (
        <Pagination
          page={page}
          total={data.total}
          size={PAGE_SIZE}
          onChange={(next) => {
            setPage(next)
            window.scrollTo({ top: 0, behavior: 'smooth' })
          }}
          label="작업 이야기 페이지"
        />
      )}
    </div>
  )
}

function postHref(p: PostSummary) {
  return p.state && p.state !== 'PUBLISHED' ? '/posts/' + p.id + '/edit' : '/posts/' + p.id
}

function Meta({ post }: { post: PostSummary }) {
  return (
    <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-muted-foreground">
      <time dateTime={post.publishedAt ?? post.createdAt}>
        {formatDate(post.publishedAt ?? post.createdAt)}
      </time>
      <span className="inline-flex items-center gap-1">
        <MessageCircle className="size-3.5" aria-hidden="true" />
        댓글 {post.commentCount}
      </span>
    </p>
  )
}

function Badges({ post }: { post: PostSummary }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      <span className="badge badge-brand">{post.category?.name ?? '미분류'}</span>
      {post.state && post.state !== 'PUBLISHED' && (
        <span className={'badge ' + (post.state === 'DRAFT' ? 'badge-warning' : '')}>
          {stateLabels[post.state]}
        </span>
      )}
    </div>
  )
}

function Cover({ post, sizes }: { post: PostSummary; sizes: string }) {
  return (
    <div className="relative aspect-[16/10] overflow-hidden rounded-xl bg-muted">
      {post.coverObjectKey && (
        <Image
          unoptimized
          fill
          sizes={sizes}
          className="object-cover transition-transform duration-500 group-hover:scale-[1.03]"
          src={storageUrl(post.coverObjectKey)}
          alt=""
        />
      )}
    </div>
  )
}

function FeaturedPost({ post }: { post: PostSummary }) {
  return (
    <Link
      href={postHref(post)}
      className="group mb-10 grid gap-6 md:grid-cols-[3fr_2fr] md:items-center md:gap-10"
    >
      <Cover post={post} sizes="(max-width:768px) 100vw, 60vw" />
      <div className="flex flex-col gap-3">
        <Badges post={post} />
        <h2 className="text-2xl font-bold leading-snug group-hover:text-primary md:text-[28px]">
          {post.title || '제목 없는 임시글'}
        </h2>
        <p className="line-clamp-3 text-muted-foreground">{post.excerpt}</p>
        <Meta post={post} />
      </div>
    </Link>
  )
}

function PostRow({ post }: { post: PostSummary }) {
  return (
    <Link href={postHref(post)} className="group flex gap-4 sm:gap-6">
      <div className="w-28 shrink-0 sm:w-56">
        <Cover post={post} sizes="(max-width:640px) 112px, 224px" />
      </div>
      <div className="flex min-w-0 flex-col gap-2">
        <Badges post={post} />
        <h2 className="line-clamp-2 text-base font-bold leading-snug group-hover:text-primary sm:text-lg">
          {post.title || '제목 없는 임시글'}
        </h2>
        <p className="line-clamp-2 hidden text-sm text-muted-foreground sm:block">{post.excerpt}</p>
        <Meta post={post} />
      </div>
    </Link>
  )
}
