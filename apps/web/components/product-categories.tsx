'use client'
import { useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { confirmAction } from '@/components/confirm-dialog'
import { LoadError } from '@/components/operator-only'
import { ListSkeleton } from '@/components/ui-kit'
import { useApi } from '@/hooks/use-api'
import { mutate, type ProductCategory } from '@/lib/api'

/** 운영 설정의 상품 분류 추가·이름 변경·삭제 */
export function ProductCategoryCard() {
  const result = useApi<ProductCategory[]>('/admin/products/categories')
  const [adding, setAdding] = useState(''),
    [editing, setEditing] = useState<{ id: string; name: string } | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  const categories = result.data ?? []

  async function run(work: () => Promise<unknown>, done: string) {
    setBusy(true)
    setError('')
    try {
      await work()
      toast.success(done)
      result.reload()
      return true
    } catch (e) {
      setError((e as Error).message)
      return false
    } finally {
      setBusy(false)
    }
  }
  async function add(e: React.FormEvent) {
    e.preventDefault()
    const name = adding.trim()
    if (!name) return
    if (await run(() => mutate('/admin/products/categories', 'POST', { name }), `'${name}' 분류를 추가했습니다.`))
      setAdding('')
  }
  async function rename(e: React.FormEvent) {
    e.preventDefault()
    if (!editing?.name.trim()) return
    const name = editing.name.trim()
    if (
      await run(
        () => mutate('/admin/products/categories/' + editing.id, 'PATCH', { name }),
        `분류 이름을 '${name}'(으)로 바꿨습니다.`,
      )
    )
      setEditing(null)
  }
  async function remove(category: ProductCategory) {
    const ok = await confirmAction({
      title: `'${category.name}' 분류를 삭제할까요?`,
      description:
        category.productCount > 0
          ? `이 분류의 상품 ${category.productCount}개는 분류 없음으로 바뀝니다.`
          : '이 분류를 쓰는 상품은 없습니다.',
      confirmLabel: '삭제',
      tone: 'danger',
    })
    if (ok)
      await run(
        () => mutate('/admin/products/categories/' + category.id, 'DELETE'),
        `'${category.name}' 분류를 삭제했습니다.`,
      )
  }

  return (
    <section className="card" aria-labelledby="settings-product-categories">
      <div className="card-body flex flex-col gap-5">
        <div>
          <h2 id="settings-product-categories" className="section-title">
            상품 분류
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            상품을 등록할 때 고르는 분류입니다. 공개 상품이 있는 분류만 상품 목록의 분류 필터에 보입니다.
          </p>
        </div>
        {result.error ? (
          <LoadError message={result.error} onRetry={result.reload} />
        ) : result.loading ? (
          <ListSkeleton rows={2} />
        ) : categories.length === 0 ? (
          <p className="rounded-xl bg-muted px-4 py-6 text-center text-sm text-muted-foreground">
            아직 분류가 없습니다. 아래에서 첫 분류를 추가하세요.
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-xl border border-border">
            {categories.map((category) => (
              <li key={category.id} className="flex min-h-14 items-center gap-3 px-4 py-2">
                {editing?.id === category.id ? (
                  <form className="flex flex-1 flex-wrap items-center gap-2" onSubmit={rename}>
                    <input
                      autoFocus
                      className="control min-w-0 flex-1"
                      aria-label={`'${category.name}' 새 이름`}
                      maxLength={50}
                      value={editing.name}
                      onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                    />
                    <button className="button sm" disabled={busy || !editing.name.trim()}>
                      변경
                    </button>
                    <button type="button" className="button ghost sm" onClick={() => setEditing(null)}>
                      취소
                    </button>
                  </form>
                ) : (
                  <>
                    <span className="flex-1 font-semibold">{category.name}</span>
                    <span className="text-sm text-muted-foreground">상품 {category.productCount}개</span>
                    <button
                      type="button"
                      className="icon-button"
                      aria-label={`'${category.name}' 이름 변경`}
                      disabled={busy}
                      onClick={() => setEditing({ id: category.id, name: category.name })}
                    >
                      <Pencil className="size-4" />
                    </button>
                    <button
                      type="button"
                      className="icon-button text-destructive"
                      aria-label={`'${category.name}' 삭제`}
                      disabled={busy}
                      onClick={() => void remove(category)}
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
        <form className="flex gap-2" onSubmit={add}>
          <input
            className="control min-w-0 flex-1"
            aria-label="새 분류 이름"
            placeholder="새 분류 이름 (예: 도마)"
            maxLength={50}
            value={adding}
            onChange={(e) => setAdding(e.target.value)}
          />
          <button className="button secondary" disabled={busy || !adding.trim()}>
            <Plus className="size-4" aria-hidden="true" />
            추가
          </button>
        </form>
        {error && (
          <p role="alert" className="field-error">
            {error}
          </p>
        )}
      </div>
    </section>
  )
}
