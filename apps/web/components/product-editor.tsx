'use client'
import { useEffect, useState } from 'react'
import { Dialog, DialogContent, DialogTitle } from '@damdam/ui'
import { toast } from 'sonner'
import {
  fetchApi,
  mutate,
  uploadImage,
  storageUrl,
  adjustInventory,
  type ApiProduct,
  type ApiInventoryAdjustment,
  type ProductCategory,
} from '@/lib/api'
import { AppSelect } from '@/components/app-select'
import { useApi } from '@/hooks/use-api'
import { ArrowLeft, ArrowRight, ImagePlus, Plus, X } from 'lucide-react'
type Variant = {
  id?: string
  name: string
  priceAmount: number
  stock: number
  isDefault: boolean
  active: boolean
}
export function ProductEditor({
  open,
  product,
  onClose,
  onSaved,
}: {
  open: boolean
  product: ApiProduct | null
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(''),
    [description, setDescription] = useState(''),
    [category, setCategory] = useState(''),
    [visible, setVisible] = useState(false),
    [variants, setVariants] = useState<Variant[]>([]),
    [images, setImages] = useState<string[]>([]),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [uploading, setUploading] = useState(false),
    [newCategory, setNewCategory] = useState<string | null>(null)
  const categoryList = useApi<ProductCategory[]>(open ? '/admin/products/categories' : null)
  const categoryNames = (categoryList.data ?? []).map((c) => c.name)
  // 예전에 자유 입력한 분류가 목록에 없어도 현재 값은 보이게 유지
  if (category && categoryList.data && !categoryNames.includes(category)) categoryNames.push(category)
  async function addCategory() {
    const name = newCategory?.trim()
    if (!name) return
    try {
      await mutate('/admin/products/categories', 'POST', { name })
      categoryList.reload()
      setCategory(name)
      setNewCategory(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }
  useEffect(() => {
    if (!open) return
    setName(product?.name ?? '')
    setDescription(product?.description ?? '')
    setCategory(product?.category ?? '')
    setNewCategory(null)
    setVisible(product?.visible ?? false)
    setImages(product?.images.map((i) => i.objectKey) ?? [])
    setVariants(
      product?.variants.map(({ id, name, priceAmount, stock, isDefault, active }) => ({
        id,
        name,
        priceAmount,
        stock,
        isDefault,
        active,
      })) ?? [{ name: '기본', priceAmount: 0, stock: 0, isDefault: true, active: true }],
    )
    setError('')
  }, [open, product])
  function changeVariant(index: number, patch: Partial<Variant>) {
    setVariants((items) =>
      items.map((v, i) =>
        i === index ? { ...v, ...patch } : patch.isDefault ? { ...v, isDefault: false } : v,
      ),
    )
  }
  async function save() {
    setBusy(true)
    setError('')
    try {
      await mutate(
        '/admin/products' + (product ? '/' + product.id : ''),
        product ? 'PATCH' : 'POST',
        {
          name,
          description,
          category,
          visible,
          variants: variants.map(({ id, name, priceAmount, stock, isDefault, active }) => ({
            id,
            name,
            priceAmount,
            isDefault,
            active,
            ...(!id ? { stock } : {}),
          })),
          images: images.map((objectKey) => ({ objectKey })),
        },
      )
      toast.success('상품을 저장했습니다.')
      onSaved()
      onClose()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }
  async function upload(files: FileList | null) {
    if (!files) return
    setError('')
    setUploading(true)
    try {
      if (images.length + files.length > 20) throw new Error('사진은 최대 20장입니다.')
      const added: string[] = []
      for (const file of Array.from(files)) added.push(await uploadImage(file, 'PRODUCT'))
      setImages((old) => [...old, ...added])
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setUploading(false)
    }
  }
  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!value && !busy && !uploading) onClose()
      }}
    >
      <DialogContent className="max-h-[90vh] overflow-y-auto rounded-2xl sm:max-w-3xl">
        <DialogTitle className="text-xl">{product ? '상품 수정' : '상품 추가'}</DialogTitle>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void save()
          }}
          className="space-y-5"
        >
          <label className="block text-sm">
            상품명
            <input
              className="control mt-1"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={200}
            />
          </label>
          <div className="text-sm">
            <label className="block">
              분류
              <AppSelect
                className="mt-1"
                label="분류"
                value={category}
                onValueChange={setCategory}
                options={[
                  { value: '', label: '분류 없음' },
                  ...categoryNames.map((n) => ({ value: n, label: n })),
                ]}
              />
            </label>
            {newCategory === null ? (
              <button
                type="button"
                className="button ghost sm mt-2"
                onClick={() => setNewCategory('')}
              >
                <Plus className="size-4" aria-hidden="true" />
                새 분류 추가
              </button>
            ) : (
              <div className="mt-2 flex gap-2">
                <input
                  autoFocus
                  className="control min-w-0 flex-1"
                  aria-label="새 분류 이름"
                  placeholder="새 분류 이름"
                  maxLength={50}
                  value={newCategory}
                  onChange={(e) => setNewCategory(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      void addCategory()
                    }
                  }}
                />
                <button
                  type="button"
                  className="button sm self-center"
                  disabled={!newCategory.trim()}
                  onClick={() => void addCategory()}
                >
                  추가
                </button>
                <button
                  type="button"
                  className="button ghost sm self-center"
                  onClick={() => setNewCategory(null)}
                >
                  취소
                </button>
              </div>
            )}
          </div>
          <label className="block text-sm">
            설명
            <textarea
              className="control mt-1 min-h-32"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          <label className="flex gap-2 text-sm">
            <input
              type="checkbox"
              checked={visible}
              onChange={(e) => setVisible(e.target.checked)}
            />
            상품 공개
          </label>
          <fieldset className="space-y-3">
            <legend className="section-title mb-3">옵션과 가격</legend>
            {variants.map((v, index) => (
              <div key={v.id ?? index} className="rounded-xl border border-border bg-muted/40 p-4">
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="text-sm">
                    옵션명
                    <input
                      className="control mt-1"
                      value={v.name}
                      onChange={(e) => changeVariant(index, { name: e.target.value })}
                      required
                    />
                  </label>
                  <label className="text-sm">
                    가격 (원)
                    <input
                      type="number"
                      min={0}
                      className="control mt-1"
                      value={v.priceAmount}
                      onChange={(e) =>
                        changeVariant(index, { priceAmount: Number(e.target.value) })
                      }
                      required
                    />
                  </label>
                </div>
                <div className="mt-3 flex flex-wrap gap-4 text-sm">
                  <label className="flex gap-1">
                    <input
                      type="radio"
                      name="default-variant"
                      checked={v.isDefault}
                      onChange={() => changeVariant(index, { isDefault: true })}
                    />
                    기본 옵션
                  </label>
                  <label className="flex gap-1">
                    <input
                      type="checkbox"
                      checked={v.active}
                      onChange={(e) => changeVariant(index, { active: e.target.checked })}
                    />
                    판매 중
                  </label>
                  <span>재고 {v.stock}개</span>
                  {!v.id && (
                    <label>
                      초기 재고
                      <input
                        type="number"
                        min={0}
                        className="control mt-1"
                        value={v.stock}
                        onChange={(e) => changeVariant(index, { stock: Number(e.target.value) })}
                      />
                    </label>
                  )}
                </div>
                {v.id && (
                  <StockPanel
                    id={v.id}
                    stock={v.stock}
                    onAdjusted={(stock) => changeVariant(index, { stock })}
                  />
                )}
              </div>
            ))}
            <button
              className="button secondary"
              type="button"
              onClick={() =>
                setVariants((old) => [
                  ...old,
                  { name: '', priceAmount: 0, stock: 0, isDefault: false, active: true },
                ])
              }
            >
              <Plus className="size-4" aria-hidden="true" />
              옵션 추가
            </button>
          </fieldset>
          <fieldset>
            <legend className="section-title mb-3">사진</legend>
            <div className="flex flex-wrap gap-3">
              {images.map((key, index) => (
                <div key={key + index} className="w-28">
                  <img
                    src={storageUrl(key)}
                    alt={'상품 사진 ' + (index + 1)}
                    className="aspect-square w-full rounded-lg object-cover"
                  />
                  <div className="mt-1.5 flex justify-between text-muted-foreground [&_button]:flex [&_button]:size-8 [&_button]:items-center [&_button]:justify-center [&_button]:rounded-md [&_button:hover]:bg-muted [&_button:disabled]:opacity-30">
                    <button
                      type="button"
                      disabled={index === 0}
                      aria-label="사진 앞으로"
                      onClick={() =>
                        setImages((old) => {
                          const next = [...old]
                          ;[next[index - 1], next[index]] = [next[index], next[index - 1]]
                          return next
                        })
                      }
                    >
                      <ArrowLeft className="size-4" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      disabled={index === images.length - 1}
                      aria-label="사진 뒤로"
                      onClick={() =>
                        setImages((old) => {
                          const next = [...old]
                          ;[next[index + 1], next[index]] = [next[index], next[index + 1]]
                          return next
                        })
                      }
                    >
                      <ArrowRight className="size-4" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      aria-label={'사진 ' + (index + 1) + ' 삭제'}
                      className="text-destructive"
                      onClick={() => setImages((old) => old.filter((_, i) => i !== index))}
                    >
                      <X className="size-4" aria-hidden="true" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <label className="button secondary mt-3 cursor-pointer">
              <ImagePlus className="size-4" aria-hidden="true" />
              {uploading ? '올리는 중…' : '사진 추가'}
              <input
                className="hidden"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                disabled={uploading}
                onChange={(e) => void upload(e.target.files)}
              />
            </label>
          </fieldset>
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <button
              className="button secondary"
              type="button"
              onClick={onClose}
              disabled={busy || uploading}
            >
              취소
            </button>
            <button className="button" disabled={busy || uploading}>
              {busy ? '저장 중…' : '저장'}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
function StockPanel({
  id,
  stock,
  onAdjusted,
}: {
  id: string
  stock: number
  onAdjusted: (stock: number) => void
}) {
  const [open, setOpen] = useState(false),
    [delta, setDelta] = useState(0),
    [reason, setReason] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  const history = useApi<{ items: ApiInventoryAdjustment[] }>(
    open ? '/admin/inventory/variants/' + id + '/history' : null,
  )
  async function adjust() {
    setBusy(true)
    setError('')
    try {
      if (!delta || !reason.trim()) throw new Error('조정 수량과 사유를 입력해주세요.')
      const result = await adjustInventory(id, { delta, reason })
      onAdjusted(result.stock)
      setDelta(0)
      setReason('')
      history.reload()
      toast.success('재고를 조정했습니다.')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="mt-3">
      <button type="button" className="link text-sm" aria-expanded={open} onClick={() => setOpen(!open)}>
        재고 조정·기록 {open ? '닫기' : '보기'}
      </button>
      {open && (
        <div className="mt-3 space-y-3">
          <p className="text-sm">현재 재고 {stock}개</p>
          <div className="flex gap-2">
            <label className="text-sm">
              증감 수량
              <input
                type="number"
                className="control"
                value={delta}
                onChange={(e) => setDelta(Number(e.target.value))}
              />
            </label>
            <label className="flex-1 text-sm">
              조정 사유
              <input
                className="control"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          </div>
          <button
            type="button"
            className="button secondary"
            disabled={busy}
            onClick={() => void adjust()}
          >
            조정 적용
          </button>
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
          {history.error && <p className="field-error">{history.error}</p>}
          <ul className="max-h-40 overflow-y-auto text-sm">
            {history.data?.items.map((item) => (
              <li key={item.id} className="border-t border-border py-2 text-muted-foreground">
                {new Date(item.createdAt).toLocaleDateString('ko-KR')} · {item.delta > 0 ? '+' : ''}
                {item.delta} · {item.reason}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
export async function loadEditableProduct(id: string) {
  return fetchApi<ApiProduct>('/admin/products/' + id)
}
