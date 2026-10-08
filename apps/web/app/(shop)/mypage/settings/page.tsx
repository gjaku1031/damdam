'use client'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { OperatorOnly, LoadError } from '@/components/operator-only'
import { AdminHeader } from '@/components/admin-nav'
import { ListSkeleton } from '@/components/ui-kit'
import { useApi } from '@/hooks/use-api'
import { mutate } from '@/lib/api'

type Config = { configKey: string; configValue: string; description: string | null }
type FieldDef = {
  key: string
  label: string
  description: string
  hint?: string
  placeholder?: string
  multiline?: boolean
  type?: string
}
type Section = { title: string; summary: string; fields: FieldDef[] }

const sections: Section[] = [
  {
    title: '입금 안내',
    summary: '주문 화면, 주문 완료 화면, 주문 내역에서 고객에게 보여주는 무통장 입금 계좌입니다.',
    fields: [
      {
        key: 'bank_transfer_account',
        label: '입금 계좌',
        description: '무통장 입금 계좌 안내',
        hint: '은행 이름, 계좌번호, 예금주를 적어주세요. 줄을 바꿔 여러 줄로 쓸 수 있습니다.',
        placeholder: '예: 국민은행 123456-78-901234 (예금주 담담공방)',
        multiline: true,
      },
    ],
  },
  {
    title: '사업자 정보',
    summary: '전자상거래법에 따라 모든 화면 아래(바닥글)에 표시해야 하는 정보입니다. 입력한 항목만 표시됩니다.',
    fields: [
      { key: 'business_name', label: '상호', description: '상호' },
      { key: 'business_owner', label: '대표자', description: '대표자' },
      {
        key: 'business_number',
        label: '사업자등록번호',
        description: '사업자등록번호',
        placeholder: '000-00-00000',
      },
      {
        key: 'mail_order_number',
        label: '통신판매업 신고번호',
        description: '통신판매업 신고번호',
        placeholder: '예: 제2026-서울성동-0000호',
      },
      { key: 'business_address', label: '사업장 주소', description: '사업장 주소' },
    ],
  },
  {
    title: '고객센터',
    summary: '바닥글에 표시되는 연락처입니다.',
    fields: [
      {
        key: 'cs_phone',
        label: '전화번호',
        description: '고객센터 전화번호',
        type: 'tel',
        placeholder: '02-000-0000',
      },
      {
        key: 'cs_hours',
        label: '운영 시간',
        description: '고객센터 운영 시간',
        placeholder: '예: 평일 10:00~17:00 (점심 12:00~13:00)',
      },
      { key: 'cs_email', label: '이메일', description: '고객센터 이메일', type: 'email' },
    ],
  },
]

const knownKeys = new Set(sections.flatMap((s) => s.fields.map((f) => f.key)))

export default function Page() {
  return (
    <OperatorOnly>
      <Settings />
    </OperatorOnly>
  )
}

function Settings() {
  const configs = useApi<Config[]>('/admin/system/configs')
  const saved = Object.fromEntries((configs.data ?? []).map((c) => [c.configKey, c.configValue]))
  const extra: Section | null = configs.data?.some((c) => !knownKeys.has(c.configKey))
    ? {
        title: '기타 설정',
        summary: '위 항목에 속하지 않는 운영 설정입니다.',
        fields: configs.data
          .filter((c) => !knownKeys.has(c.configKey))
          .map((c) => ({
            key: c.configKey,
            label: c.description || c.configKey,
            description: c.description || c.configKey,
          })),
      }
    : null
  return (
    <div className="page">
      <AdminHeader title="운영 설정" description="결제 수단은 무통장 입금입니다. 고객에게 보이는 안내 정보를 관리합니다." />
      {configs.error && <LoadError message={configs.error} onRetry={configs.reload} />}
      {configs.loading && <ListSkeleton rows={3} />}
      {configs.data && (
        <div className="flex max-w-3xl flex-col gap-5">
          {[...sections, ...(extra ? [extra] : [])].map((section) => (
            <SettingsCard key={section.title} section={section} saved={saved} onSaved={configs.reload} />
          ))}
        </div>
      )}
    </div>
  )
}

function SettingsCard({
  section,
  saved,
  onSaved,
}: {
  section: Section
  saved: Record<string, string>
  onSaved: () => void
}) {
  const initial = () => Object.fromEntries(section.fields.map((f) => [f.key, saved[f.key] ?? '']))
  const [values, setValues] = useState<Record<string, string>>(initial),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  const savedKey = section.fields.map((f) => saved[f.key] ?? '').join('\u0000')
  // 저장 후 다시 불러온 값으로 맞춤
  useEffect(() => setValues(initial()), [savedKey])
  const changed = section.fields.filter((f) => (values[f.key] ?? '') !== (saved[f.key] ?? ''))

  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (!changed.length) return
    setBusy(true)
    setError('')
    try {
      for (const field of changed) {
        await mutate('/admin/system/configs', 'POST', {
          key: field.key,
          value: values[field.key].trim(),
          description: field.description,
        })
      }
      toast.success(`${section.title}을(를) 저장했습니다.`)
      onSaved()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card" onSubmit={save} aria-labelledby={'settings-' + section.title}>
      <div className="card-body flex flex-col gap-5">
        <div>
          <h2 id={'settings-' + section.title} className="section-title">
            {section.title}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">{section.summary}</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          {section.fields.map((field) => {
            const hintId = field.hint ? 'hint-' + field.key : undefined
            const wide = field.multiline || field.key === 'business_address' || section.fields.length === 1
            return (
              <label key={field.key} className={`field ${wide ? 'sm:col-span-2' : ''}`}>
                {field.label}
                {field.multiline ? (
                  <textarea
                    className="control min-h-24"
                    value={values[field.key] ?? ''}
                    placeholder={field.placeholder}
                    aria-describedby={hintId}
                    onChange={(e) => setValues((v) => ({ ...v, [field.key]: e.target.value }))}
                  />
                ) : (
                  <input
                    className="control"
                    type={field.type ?? 'text'}
                    value={values[field.key] ?? ''}
                    placeholder={field.placeholder}
                    aria-describedby={hintId}
                    onChange={(e) => setValues((v) => ({ ...v, [field.key]: e.target.value }))}
                  />
                )}
                {field.hint && (
                  <span id={hintId} className="field-hint">
                    {field.hint}
                  </span>
                )}
              </label>
            )
          })}
        </div>
        {error && (
          <p role="alert" className="field-error">
            {error}
          </p>
        )}
        <div className="flex items-center justify-end gap-3 border-t border-border pt-4">
          {changed.length > 0 && (
            <span className="text-sm text-muted-foreground">저장하지 않은 변경 {changed.length}개</span>
          )}
          <button className="button" disabled={busy || !changed.length} aria-busy={busy}>
            {busy ? '저장 중…' : '저장'}
          </button>
        </div>
      </div>
    </form>
  )
}
