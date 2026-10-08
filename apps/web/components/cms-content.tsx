'use client'
import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { ChevronDown, ChevronLeft, Megaphone, MessageCircleQuestion, Plus, Search } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@damdam/ui'
import { toast } from 'sonner'
import { useApi } from '@/hooks/use-api'
import { useAuthStore } from '@/lib/store'
import { mutate, type ApiCmsContent } from '@/lib/api'
import { formatDate } from '@/lib/format-date'
import { RichTextEditor } from './rich-text-editor'
import { LoadError } from './operator-only'
import { EmptyState, ListSkeleton, PageHeader, PageLoading, Pagination } from './ui-kit'
import { confirmAction } from './confirm-dialog'
import { CsContactCard } from './cs-contact'

const NOTICE_PAGE_SIZE = 10

/** 본문 HTML에서 미리보기 문장만 추출 */
export function plainText(html: string) {
  return html
    .replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim()
}

type Editing = { item: ApiCmsContent | null } | null

function useCmsActions(type: 'faqs' | 'notices', reload: () => void) {
  const [error, setError] = useState('')
  async function toggle(item: ApiCmsContent) {
    setError('')
    try {
      await mutate(
        '/admin/cms/' + type + '/' + item.id + (item.visible ? '/hide' : '/show'),
        'POST',
      )
      toast.success(item.visible ? '숨겼습니다.' : '공개했습니다.')
      reload()
    } catch (e) {
      setError((e as Error).message)
    }
  }
  async function remove(item: ApiCmsContent) {
    const ok = await confirmAction({
      title: '이 항목을 삭제할까요?',
      description: `"${item.title}"을 삭제하면 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      tone: 'danger',
    })
    if (!ok) return false
    setError('')
    try {
      await mutate('/admin/cms/' + type + '/' + item.id, 'DELETE')
      toast.success('삭제했습니다.')
      reload()
      return true
    } catch (e) {
      setError((e as Error).message)
      return false
    }
  }
  return { error, toggle, remove }
}

function AdminControls({
  item,
  onEdit,
  onToggle,
  onRemove,
}: {
  item: ApiCmsContent
  onEdit: () => void
  onToggle: () => void
  onRemove: () => void
}) {
  return (
    <div className="flex flex-wrap justify-end gap-1 border-t border-border px-4 py-2">
      <button type="button" className="button ghost sm" onClick={onEdit}>
        수정
      </button>
      <button type="button" className="button ghost sm" onClick={onToggle}>
        {item.visible ? '숨기기' : '공개하기'}
      </button>
      <button type="button" className="button ghost sm text-destructive" onClick={onRemove}>
        삭제
      </button>
    </div>
  )
}

export function CmsList({ faq = false }: { faq?: boolean }) {
  return faq ? <FaqList /> : <NoticeList />
}

function NoticeList() {
  const edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN')
  const [page, setPage] = useState(1)
  const data = useApi<{ items: ApiCmsContent[]; total: number }>(
    (edit ? '/admin/cms/notices' : '/notices') + '?page=' + page + '&size=' + NOTICE_PAGE_SIZE,
  )
  const [editing, setEditing] = useState<Editing>(null)
  const actions = useCmsActions('notices', data.reload)
  return (
    <div className="page page-narrow">
      <PageHeader
        title="공지사항"
        description="배송·휴무·공방 운영 소식을 알려드립니다."
        actions={
          edit && (
            <button className="button" onClick={() => setEditing({ item: null })}>
              <Plus className="size-4" aria-hidden="true" />
              공지 작성
            </button>
          )
        }
      />
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {actions.error && (
        <p role="alert" className="field-error mb-4">
          {actions.error}
        </p>
      )}
      {data.loading && !data.data && <ListSkeleton rows={4} />}
      {data.data && data.data.items.length === 0 && (
        <EmptyState icon={<Megaphone className="size-5" />} title="등록된 공지사항이 없습니다" />
      )}
      {data.data && data.data.items.length > 0 && (
        <ul className="flex flex-col gap-3">
          {data.data.items.map((item) => {
            const excerpt = plainText(item.bodyHtml)
            return (
              <li key={item.id} className="card overflow-hidden">
                <Link
                  className="group flex flex-col gap-2 p-5 hover:bg-muted/40"
                  href={'/notices/' + item.id}
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-base font-semibold group-hover:text-primary">
                      {item.title}
                    </h2>
                    {!item.visible && <span className="badge">비공개</span>}
                  </div>
                  {excerpt && <p className="line-clamp-2 text-sm text-muted-foreground">{excerpt}</p>}
                  <time className="text-[13px] text-subtle-foreground" dateTime={item.createdAt}>
                    {formatDate(item.createdAt)}
                  </time>
                </Link>
                {edit && (
                  <AdminControls
                    item={item}
                    onEdit={() => setEditing({ item })}
                    onToggle={() => void actions.toggle(item)}
                    onRemove={() => void actions.remove(item)}
                  />
                )}
              </li>
            )
          })}
        </ul>
      )}
      {data.data && (
        <Pagination
          page={page}
          total={data.data.total}
          size={NOTICE_PAGE_SIZE}
          onChange={(next) => {
            setPage(next)
            window.scrollTo({ top: 0, behavior: 'smooth' })
          }}
          label="공지사항 페이지"
        />
      )}
      <CmsEditor editing={editing} type="notices" onClose={() => setEditing(null)} onSaved={data.reload} />
    </div>
  )
}

function FaqList() {
  const edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN')
  const data = useApi<{ items: ApiCmsContent[] }>((edit ? '/admin/cms/faqs' : '/faqs') + '?size=100')
  const [editing, setEditing] = useState<Editing>(null),
    [open, setOpen] = useState<string | null>(null),
    [query, setQuery] = useState('')
  const actions = useCmsActions('faqs', data.reload)
  const items = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    const all = data.data?.items ?? []
    if (!keyword) return all
    return all.filter(
      (item) =>
        item.title.toLowerCase().includes(keyword) ||
        plainText(item.bodyHtml).toLowerCase().includes(keyword),
    )
  }, [data.data, query])
  return (
    <div className="page page-narrow">
      <PageHeader
        title="자주 묻는 질문"
        description="주문·입금·배송·교환에 대해 자주 받는 질문을 모았습니다."
        actions={
          edit && (
            <button className="button" onClick={() => setEditing({ item: null })}>
              <Plus className="size-4" aria-hidden="true" />
              질문 추가
            </button>
          )
        }
      />
      {(data.data?.items.length ?? 0) > 3 && (
        <div className="relative mb-6">
          <Search
            className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-subtle-foreground"
            aria-hidden="true"
          />
          <input
            type="search"
            className="control pl-10"
            aria-label="질문 검색"
            placeholder="궁금한 내용을 검색하세요"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      )}
      {data.error && <LoadError message={data.error} onRetry={data.reload} />}
      {actions.error && (
        <p role="alert" className="field-error mb-4">
          {actions.error}
        </p>
      )}
      {data.loading && !data.data && <ListSkeleton rows={4} />}
      {data.data && items.length === 0 && (
        <EmptyState
          icon={<MessageCircleQuestion className="size-5" />}
          title={query ? '찾는 질문이 없습니다' : '등록된 질문이 없습니다'}
          description={query ? '다른 낱말로 검색해보세요.' : undefined}
        />
      )}
      {items.length > 0 && (
        <ul className="flex flex-col gap-3">
          {items.map((item) => {
            const expanded = open === item.id
            return (
              <li key={item.id} className="card overflow-hidden">
                <h2>
                  <button
                    type="button"
                    id={'faq-q-' + item.id}
                    aria-expanded={expanded}
                    aria-controls={'faq-a-' + item.id}
                    onClick={() => setOpen(expanded ? null : item.id)}
                    className="flex min-h-14 w-full items-center gap-3 px-5 py-4 text-left text-[15px] font-semibold hover:bg-muted/40"
                  >
                    <span className="text-primary" aria-hidden="true">
                      Q
                    </span>
                    <span className="flex-1">
                      {item.title}
                      {!item.visible && <span className="badge ml-2 align-middle">비공개</span>}
                    </span>
                    <ChevronDown
                      className={`size-5 shrink-0 text-muted-foreground transition-transform ${
                        expanded ? 'rotate-180' : ''
                      }`}
                      aria-hidden="true"
                    />
                  </button>
                </h2>
                {expanded && (
                  <div
                    id={'faq-a-' + item.id}
                    role="region"
                    aria-labelledby={'faq-q-' + item.id}
                    className="article-body border-t border-border bg-muted/30 px-5 py-4 text-[15px] [&>*:first-child]:mt-0 [&>*:last-child]:mb-0"
                    dangerouslySetInnerHTML={{ __html: item.bodyHtml }}
                  />
                )}
                {edit && (
                  <AdminControls
                    item={item}
                    onEdit={() => setEditing({ item })}
                    onToggle={() => void actions.toggle(item)}
                    onRemove={() => void actions.remove(item)}
                  />
                )}
              </li>
            )
          })}
        </ul>
      )}
      <CsContactCard />
      <CmsEditor editing={editing} type="faqs" onClose={() => setEditing(null)} onSaved={data.reload} />
    </div>
  )
}

export function NoticeDetail({ id }: { id: string }) {
  const edit = useAuthStore((s) => s.editMode && s.user?.role === 'ADMIN'),
    data = useApi<ApiCmsContent>((edit ? '/admin/cms/notices/' : '/notices/') + id),
    [editing, setEditing] = useState<Editing>(null)
  if (data.error)
    return (
      <div className="page page-narrow">
        <LoadError message={data.error} onRetry={data.reload} />
        <Link href="/notices" className="link text-sm">
          공지사항 목록
        </Link>
      </div>
    )
  if (!data.data) return <PageLoading label="공지사항을 불러오는 중" />
  const notice = data.data
  return (
    <div className="page page-narrow">
      <div className="mb-8 flex items-center justify-between gap-3">
        <Link
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          href="/notices"
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
          공지사항
        </Link>
        {edit && (
          <button className="button secondary sm" onClick={() => setEditing({ item: notice })}>
            수정
          </button>
        )}
      </div>
      <article>
        <header className="border-b border-border pb-8">
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge badge-brand">공지</span>
            {!notice.visible && <span className="badge">비공개</span>}
          </div>
          <h1 className="mt-4 text-[28px] font-bold leading-tight">{notice.title}</h1>
          <time className="mt-3 block text-sm text-muted-foreground" dateTime={notice.createdAt}>
            {formatDate(notice.createdAt)}
          </time>
        </header>
        <div
          className="article-body mt-8 [&_img]:mx-auto [&_img]:max-h-[720px] [&_img]:w-auto [&_img]:rounded-xl"
          dangerouslySetInnerHTML={{ __html: notice.bodyHtml }}
        />
      </article>
      <div className="mt-12 border-t border-border pt-6">
        <Link href="/notices" className="button secondary">
          목록으로
        </Link>
      </div>
      <CmsEditor editing={editing} type="notices" onClose={() => setEditing(null)} onSaved={data.reload} />
    </div>
  )
}

function CmsEditor({
  editing,
  type,
  onClose,
  onSaved,
}: {
  editing: Editing
  type: 'faqs' | 'notices'
  onClose: () => void
  onSaved: () => void
}) {
  const [title, setTitle] = useState(''),
    [html, setHtml] = useState(''),
    [busy, setBusy] = useState(false),
    [blocked, setBlocked] = useState(false),
    [error, setError] = useState('')
  useEffect(() => {
    if (editing) {
      setTitle(editing.item?.title ?? '')
      setHtml(editing.item?.bodyHtml ?? '<p></p>')
      setError('')
    }
  }, [editing])
  async function save() {
    setBusy(true)
    setError('')
    try {
      const item = editing?.item
      await mutate('/admin/cms/' + type + (item ? '/' + item.id : ''), item ? 'PATCH' : 'POST', {
        title,
        bodyHtml: html,
        ...(item ? { visible: item.visible } : {}),
      })
      toast.success('저장했습니다.')
      onSaved()
      onClose()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }
  const faq = type === 'faqs'
  return (
    <Dialog
      open={Boolean(editing)}
      onOpenChange={(value) => {
        if (!value && !busy && !blocked) onClose()
      }}
    >
      <DialogContent className="max-h-[90vh] overflow-y-auto rounded-2xl sm:max-w-4xl">
        <DialogTitle className="text-lg">
          {editing?.item ? (faq ? '질문 수정' : '공지 수정') : faq ? '질문 추가' : '공지 작성'}
        </DialogTitle>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void save()
          }}
          className="flex flex-col gap-5"
        >
          <label className="field">
            {faq ? '질문' : '제목'}
            <input
              className="control"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              maxLength={200}
            />
          </label>
          <div className="field">
            <span>{faq ? '답변' : '내용'}</span>
            {editing && (
              <RichTextEditor
                content={html}
                onChange={(_doc, next) => setHtml(next)}
                onUploadState={setBlocked}
                disabled={busy}
              />
            )}
          </div>
          {error && (
            <p role="alert" className="field-error">
              {error}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <button
              className="button secondary"
              type="button"
              disabled={busy || blocked}
              onClick={onClose}
            >
              취소
            </button>
            <button className="button" disabled={busy || blocked}>
              {busy ? '저장 중…' : '저장'}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
