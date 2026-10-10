'use client'
import { AppSelect } from './app-select'
import { ArrowLeft, Check, CloudUpload, Eye, Save, Settings2, ImageIcon } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import {
  ApiError,
  fetchApi,
  getDraft,
  saveDraft,
  publishPost,
  mutate,
  storageUrl,
  type PostDraft,
  type DraftResponse,
  type Category,
} from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { RichTextEditor } from './rich-text-editor'
import { OperatorOnly, LoadError } from './operator-only'
import { toast } from 'sonner'
import { confirmAction } from './confirm-dialog'
import { PageLoading } from './ui-kit'
import type { JSONContent } from '@tiptap/react'
function getImages(doc: JSONContent): string[] {
  const keys: string[] = []
  const visit = (n: JSONContent) => {
    if (n.type === 'image')
      keys.push(
        String(
          n.attrs?.objectKey || String(n.attrs?.src ?? '').replace('/api/storage/files/', ''),
        ),
      )
    n.content?.forEach(visit)
  }
  visit(doc)
  return [...new Set(keys)]
}
export function PostWriter({ id }: { id: string }) {
  return (
    <OperatorOnly>
      <Writer key={id} id={id} />
    </OperatorOnly>
  )
}
function Writer({ id }: { id: string }) {
  const user = useAuthStore((s) => s.user)!,
    router = useRouter(),
    backupKey = 'damdam:draft:' + user.id + ':' + id
  const [initial, setInitial] = useState<DraftResponse | null>(null),
    [draft, setDraft] = useState<PostDraft | null>(null),
    [categories, setCategories] = useState<Category[]>([]),
    [error, setError] = useState(''),
    [status, setStatus] = useState(''),
    [conflict, setConflict] = useState(false),
    [blocked, setBlocked] = useState(false),
    [saveFailed, setSaveFailed] = useState(false),
    [publishing, setPublishing] = useState(false),
    [preview, setPreview] = useState(false),
    [html, setHtml] = useState(''),
    [recovery, setRecovery] = useState<PostDraft | null>(null),
    [categoryForm, setCategoryForm] = useState<{ mode: 'add' | 'rename'; name: string } | null>(
      null,
    )
  const draftRef = useRef<PostDraft | null>(null),
    saved = useRef(''),
    revision = useRef(0),
    inflight = useRef<Promise<void> | null>(null),
    conflictRef = useRef(false),
    backupRef = useRef(backupKey),
    initialized = useRef(false)
  useEffect(() => {
    backupRef.current = backupKey
  }, [backupKey])
  const load = useCallback(async () => {
    setError('')
    try {
      const [result, cats] = await Promise.all([
        getDraft(id),
        fetchApi<Category[]>('/posts/categories'),
      ])
      setInitial(result)
      setDraft(result.draft)
      draftRef.current = result.draft
      saved.current = JSON.stringify(result.draft)
      revision.current = result.revision
      setCategories(cats)
      setStatus('저장됨')
      setSaveFailed(false)
      setConflict(false)
      conflictRef.current = false
      if (!initialized.current) {
        try {
          const local = JSON.parse(localStorage.getItem(backupKey) || 'null')
          if (local?.draft && JSON.stringify(local.draft) !== saved.current)
            setRecovery(local.draft)
        } catch {}
        initialized.current = true
      }
    } catch (e) {
      setError((e as Error).message)
    }
  }, [id, backupKey])
  useEffect(() => {
    void load()
  }, [load])
  const flush = useCallback(async () => {
    if (conflictRef.current)
      throw new Error('다른 화면에서 변경된 글입니다. 내용을 복사하거나 다시 불러와주세요.')
    if (inflight.current) {
      await inflight.current
      return
    }
    const current = draftRef.current
    if (!current || JSON.stringify(current) === saved.current) return
    const snapshot = JSON.stringify(current)
    setStatus('저장 중...')
    const promise = saveDraft(id, current, revision.current)
      .then((result) => {
        revision.current = result.revision
        saved.current = snapshot
        setError('')
        setSaveFailed(false)
        setStatus(JSON.stringify(draftRef.current) === snapshot ? '저장됨' : '수정 중')
        if (JSON.stringify(draftRef.current) === snapshot)
          localStorage.removeItem(backupRef.current)
      })
      .catch((e) => {
        setError(e.message)
        setStatus('저장 실패')
        setSaveFailed(true)
        if (e instanceof ApiError && e.status === 409) {
          conflictRef.current = true
          setConflict(true)
        }
        throw e
      })
      .finally(() => {
        inflight.current = null
      })
    inflight.current = promise
    await promise
  }, [id])
  function change(next: PostDraft) {
    draftRef.current = next
    setDraft(next)
    setStatus('수정 중')
    try {
      localStorage.setItem(
        backupKey,
        JSON.stringify({ draft: next, revision: revision.current, savedAt: Date.now() }),
      )
    } catch {
      setError('브라우저 복구 저장이 부족합니다. 서버 저장 상태를 확인해주세요.')
    }
  }
  useEffect(() => {
    if (!draft || blocked || conflict || publishing) return
    const timer = setTimeout(() => void flush().catch(() => {}), 2000)
    return () => clearTimeout(timer)
  }, [draft, blocked, conflict, publishing, flush])
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (blocked || JSON.stringify(draftRef.current) !== saved.current) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [blocked])
  async function publish() {
    if (blocked || conflict || saveFailed) return
    setPublishing(true)
    try {
      await flush()
      while (JSON.stringify(draftRef.current) !== saved.current) await flush()
      const result = await publishPost(id, revision.current)
      revision.current = result.revision
      setInitial(result)
      setStatus('발행됨')
      toast.success('글을 발행했습니다.')
      router.push('/posts/' + id)
    } catch (e) {
      setError((e as Error).message)
      if (e instanceof ApiError && e.status === 409) {
        conflictRef.current = true
        setConflict(true)
        try {
          localStorage.setItem(
            backupKey,
            JSON.stringify({
              draft: draftRef.current,
              revision: revision.current,
              savedAt: Date.now(),
            }),
          )
        } catch {}
      }
    } finally {
      setPublishing(false)
    }
  }
  async function addCategory(name: string) {
    if (!name.trim()) return
    try {
      const category = await mutate<Category>('/admin/posts/categories', 'POST', {
        name: name.trim(),
      })
      setCategories((old) => [...old, category])
      if (draft) change({ ...draft, categoryId: category.id })
      setCategoryForm(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }
  async function renameCategory(name: string) {
    if (!draft?.categoryId || !name.trim()) return
    try {
      const result = await mutate<Category>(
        '/admin/posts/categories/' + draft.categoryId,
        'PATCH',
        { name: name.trim() },
      )
      setCategories((old) => old.map((c) => (c.id === result.id ? result : c)))
      setCategoryForm(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }
  async function deleteCategory() {
    if (
      !draft?.categoryId ||
      !(await confirmAction({
        title: '이 분류를 삭제할까요?',
        description: '이 분류에 연결된 글은 미분류로 바뀝니다.',
        confirmLabel: '삭제',
        tone: 'danger',
      }))
    )
      return
    if (blocked || conflict || publishing) return
    try {
      await flush()
      while (JSON.stringify(draftRef.current) !== saved.current) await flush()
      await mutate('/admin/posts/categories/' + draft.categoryId, 'DELETE')
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }
  if (!draft)
    return (
      error ? (
        <div className="page page-narrow">
          <LoadError message={error} onRetry={() => void load()} />
        </div>
      ) : (
        <PageLoading label="글을 불러오는 중" />
      )
    )
  const keys = getImages(draft.documentJson)
  return (
    <div className="post-writer mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-8">
      <div className="writer-actions mb-7 flex flex-wrap items-center justify-between gap-3">
        <Link
          className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
          href="/posts"
          onClick={(e) => {
            if (blocked || publishing) {
              e.preventDefault()
              setError('사진 업로드와 저장이 끝난 뒤 이동해주세요.')
            }
          }}
        >
          <ArrowLeft size={16} aria-hidden="true" /> 작업 이야기 목록
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <span
            role="status"
            className="writer-save-status mr-2 inline-flex items-center gap-1.5 text-xs text-muted-foreground"
          >
            {['저장됨', '발행됨'].includes(status) ? (
              <Check size={14} />
            ) : (
              <CloudUpload size={14} />
            )}
            {status}
          </span>
          <button
            className="button secondary"
            disabled={blocked || conflict || publishing}
            onClick={() => void flush().catch(() => {})}
          >
            <Save size={15} aria-hidden="true" /> 임시저장
          </button>
          <button className="button secondary" onClick={() => setPreview(!preview)}>
            <Eye size={15} aria-hidden="true" /> {preview ? '편집으로' : '미리보기'}
          </button>
          <button
            className="button"
            disabled={blocked || conflict || publishing || saveFailed}
            onClick={() => void publish()}
          >
            {publishing
              ? '발행 중...'
              : initial?.state === 'PUBLISHED'
                ? '발행본 업데이트'
                : '발행'}
          </button>
        </div>
      </div>
      {recovery && (
        <div className="notice-box mb-5">
          <p>브라우저에 저장되지 않은 작성 내용이 있습니다.</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              className="button sm"
              onClick={() => {
                change(recovery)
                setRecovery(null)
              }}
            >
              복구하기
            </button>
            <button
              className="button secondary sm"
              onClick={() => {
                localStorage.removeItem(backupKey)
                setRecovery(null)
              }}
            >
              현재 서버 내용 사용
            </button>
          </div>
        </div>
      )}
      {error && (
        <div role="alert" className="card mb-4 border-destructive/40 p-4 text-sm">
          <p className="text-destructive">{error}</p>
          {conflict && (
            <div className="mt-3 flex flex-wrap gap-2">
              <button
                className="button secondary sm"
                onClick={() =>
                  void navigator.clipboard
                    .writeText(JSON.stringify(draft, null, 2))
                    .then(() => toast.success('작성 내용을 복사했습니다.'))
                    .catch(() => setError('복사 권한을 확인해주세요.'))
                }
              >
                작성 내용 복사
              </button>
              <button
                className="button secondary sm"
                onClick={() =>
                  void confirmAction({
                    title: '서버 내용을 불러올까요?',
                    description: '지금 작성 중인 내용은 브라우저 복구 초안으로 보관됩니다.',
                    confirmLabel: '불러오기',
                  }).then((ok) => {
                    if (!ok) return
                    setRecovery(draft)
                    void load()
                  })
                }
              >
                서버 내용 다시 불러오기
              </button>
            </div>
          )}
        </div>
      )}
      <p className="eyebrow mb-1">작업 이야기 · 글쓰기</p>
      <label className="block">
        <span className="sr-only">제목</span>
        <input
          className="writer-title mb-6 w-full border-b border-border bg-transparent pb-6 pt-3 text-3xl font-semibold tracking-tight outline-none placeholder:text-subtle-foreground sm:text-4xl"
          placeholder="제목을 입력하세요"
          maxLength={200}
          value={draft.title}
          disabled={publishing}
          onChange={(e) => change({ ...draft, title: e.target.value })}
        />
      </label>
      <div className={preview ? 'hidden' : ''}>
        <RichTextEditor
          content={draft.documentJson}
          disabled={publishing}
          onChange={(documentJson, bodyHtml) => {
            setHtml(bodyHtml)
            change({
              ...draftRef.current!,
              documentJson,
              coverObjectKey:
                draftRef.current!.coverObjectKey &&
                getImages(documentJson).includes(draftRef.current!.coverObjectKey!)
                  ? draftRef.current!.coverObjectKey
                  : null,
            })
          }}
          onUploadState={setBlocked}
          onReadyHtml={setHtml}
        />
      </div>
      {preview && (
        <article
          className="article-body card p-4 sm:p-8"
          dangerouslySetInnerHTML={{ __html: html || '' }}
        />
      )}
      <section
        className="writer-settings card mt-6 p-5 sm:p-6"
        aria-labelledby="writer-settings-title"
      >
        <div className="mb-5 flex items-center gap-2 border-b border-border pb-4">
          <Settings2 size={17} className="text-primary" />
          <h2 id="writer-settings-title" className="text-sm font-semibold">
            발행 설정
          </h2>
          <span className="ml-auto text-xs text-muted-foreground">
            분류 · 태그 · 대표 이미지
          </span>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label className="field">
              분류
              <AppSelect
                label="분류"
                value={draft.categoryId ?? ''}
                onValueChange={(value) => change({ ...draft, categoryId: value || null })}
                disabled={publishing}
                options={[
                  { value: '', label: '미분류' },
                  ...categories.map((c) => ({ value: c.id, label: c.name })),
                ]}
              />
            </label>
            {categoryForm ? (
              <form
                className="mt-3 flex gap-2"
                onSubmit={(e) => {
                  e.preventDefault()
                  void (categoryForm.mode === 'add'
                    ? addCategory(categoryForm.name)
                    : renameCategory(categoryForm.name))
                }}
              >
                <input
                  autoFocus
                  className="control"
                  aria-label={categoryForm.mode === 'add' ? '새 분류 이름' : '바꿀 분류 이름'}
                  placeholder={categoryForm.mode === 'add' ? '새 분류 이름' : '바꿀 이름'}
                  maxLength={40}
                  value={categoryForm.name}
                  onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })}
                />
                <button className="button sm self-center" disabled={!categoryForm.name.trim()}>
                  {categoryForm.mode === 'add' ? '추가' : '변경'}
                </button>
                <button
                  type="button"
                  className="button ghost sm self-center"
                  onClick={() => setCategoryForm(null)}
                >
                  취소
                </button>
              </form>
            ) : (
              <div className="mt-2 flex flex-wrap gap-1">
                <button
                  type="button"
                  onClick={() => setCategoryForm({ mode: 'add', name: '' })}
                  className="button ghost sm"
                >
                  분류 추가
                </button>
                {draft.categoryId && (
                  <>
                    <button
                      type="button"
                      onClick={() =>
                        setCategoryForm({
                          mode: 'rename',
                          name: categories.find((c) => c.id === draft.categoryId)?.name ?? '',
                        })
                      }
                      className="button ghost sm"
                    >
                      이름 변경
                    </button>
                    <button
                      type="button"
                      onClick={() => void deleteCategory()}
                      className="button ghost sm text-destructive"
                    >
                      삭제
                    </button>
                  </>
                )}
              </div>
            )}
          </div>
          <label className="field">
            태그 (쉼표로 구분)
            <input
              className="control"
              value={draft.tags.join(', ')}
              onChange={(e) =>
                change({
                  ...draft,
                  tags: e.target.value
                    .split(',')
                    .map((t) => t.trim())
                    .slice(0, 10),
                })
              }
              disabled={publishing}
            />
          </label>
          <label className="field sm:col-span-2">
            <span className="inline-flex items-center gap-1.5">
              <ImageIcon size={15} aria-hidden="true" /> 대표 이미지
            </span>
            <AppSelect
              label="대표 이미지"
              value={draft.coverObjectKey ?? ''}
              onValueChange={(value) => change({ ...draft, coverObjectKey: value || null })}
              disabled={publishing}
              options={[
                {
                  value: '',
                  label: '첫 사진 자동 선택',
                  description: keys.length
                    ? '본문의 첫 번째 사진을 사용합니다'
                    : '본문에 사진을 넣으면 선택할 수 있어요',
                },
                ...keys.map((key, i) => ({
                  value: key,
                  label: `사진 ${i + 1}`,
                  image: storageUrl(key, 's'),
                })),
              ]}
            />
            {draft.coverObjectKey && (
              <img
                src={storageUrl(draft.coverObjectKey, 's')}
                alt="대표 이미지"
                className="mt-1 h-20 w-32 rounded-lg object-cover"
              />
            )}
          </label>
        </div>
      </section>
    </div>
  )
}
