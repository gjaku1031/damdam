'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { MemberOnly } from '@/components/operator-only'
import { PageHeader } from '@/components/ui-kit'
import { PasswordInput } from '@/components/password-input'
import { confirmAction } from '@/components/confirm-dialog'
import { ApiError, changePassword, withdrawAccount } from '@/lib/api'
import { useAuthStore } from '@/lib/store'

export default function Page() {
  return (
    <MemberOnly>
      <Account />
    </MemberOnly>
  )
}

function Account() {
  const user = useAuthStore((s) => s.user)
  return (
    <div className="page page-narrow">
      <PageHeader
        back={{ href: '/mypage', label: user?.role === 'ADMIN' ? '운영 대시보드' : '내 계정' }}
        title="계정 설정"
        description={`아이디 ${user?.username ?? ''}`}
      />
      <div className="flex flex-col gap-5">
        <PasswordCard />
        {user?.role === 'CUSTOMER' && <WithdrawCard />}
      </div>
    </div>
  )
}

function PasswordCard() {
  const [current, setCurrent] = useState(''),
    [next, setNext] = useState(''),
    [confirm, setConfirm] = useState(''),
    [busy, setBusy] = useState(false),
    [errors, setErrors] = useState<Record<string, string>>({})

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const issues: Record<string, string> = {}
    if (!current) issues.currentPassword = '현재 비밀번호를 입력해주세요.'
    if (next.length < 12 || next.length > 128) issues.newPassword = '새 비밀번호는 12~128자로 입력해주세요.'
    else if (next === current) issues.newPassword = '현재 비밀번호와 다른 비밀번호를 입력해주세요.'
    if (next !== confirm) issues.confirm = '새 비밀번호가 일치하지 않습니다.'
    setErrors(issues)
    if (Object.keys(issues).length) return
    setBusy(true)
    try {
      await changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      toast.success('비밀번호를 바꿨습니다.')
    } catch (e) {
      setErrors(
        e instanceof ApiError && e.fields ? e.fields : { form: (e as Error).message },
      )
    } finally {
      setBusy(false)
    }
  }

  const field = (
    id: string,
    label: string,
    value: string,
    set: (v: string) => void,
    autoComplete: string,
    hint?: string,
  ) => (
    <label className="field">
      {label}
      <PasswordInput
        value={value}
        onChange={(e) => set(e.target.value)}
        autoComplete={autoComplete}
        maxLength={128}
        disabled={busy}
        aria-invalid={Boolean(errors[id])}
        aria-describedby={[hint && 'hint-' + id, errors[id] && 'error-' + id].filter(Boolean).join(' ') || undefined}
      />
      {hint && (
        <span id={'hint-' + id} className="field-hint">
          {hint}
        </span>
      )}
      {errors[id] && (
        <span id={'error-' + id} className="field-error mt-0">
          {errors[id]}
        </span>
      )}
    </label>
  )

  return (
    <form className="card" onSubmit={submit} aria-labelledby="password-title" noValidate>
      <div className="card-body flex flex-col gap-4">
        <h2 id="password-title" className="section-title">
          비밀번호 변경
        </h2>
        {field('currentPassword', '현재 비밀번호', current, setCurrent, 'current-password')}
        {field('newPassword', '새 비밀번호', next, setNext, 'new-password', '12~128자로 입력해주세요.')}
        {field('confirm', '새 비밀번호 확인', confirm, setConfirm, 'new-password')}
        {errors.form && (
          <p role="alert" className="field-error">
            {errors.form}
          </p>
        )}
        <div className="flex justify-end border-t border-border pt-4">
          <button className="button" disabled={busy} aria-busy={busy}>
            {busy ? '바꾸는 중…' : '비밀번호 바꾸기'}
          </button>
        </div>
      </div>
    </form>
  )
}

function WithdrawCard() {
  const router = useRouter()
  const [password, setPassword] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!password) {
      setError('비밀번호를 입력해주세요.')
      return
    }
    const ok = await confirmAction({
      title: '정말 탈퇴할까요?',
      description: '탈퇴하면 이 아이디로 다시 로그인할 수 없습니다. 주문 기록은 법령에 따라 보관됩니다.',
      confirmLabel: '탈퇴하기',
      tone: 'danger',
    })
    if (!ok) return
    setBusy(true)
    setError('')
    try {
      await withdrawAccount(password)
      useAuthStore.getState().clearUser()
      toast.success('탈퇴했습니다. 그동안 이용해주셔서 감사합니다.')
      router.replace('/')
    } catch (e) {
      setError(
        e instanceof ApiError && e.fields?.password ? e.fields.password : (e as Error).message,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card" onSubmit={submit} aria-labelledby="withdraw-title" noValidate>
      <div className="card-body flex flex-col gap-4">
        <div>
          <h2 id="withdraw-title" className="section-title">
            회원 탈퇴
          </h2>
          <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
            입금 대기·제작·배송 중인 주문이 있으면 탈퇴할 수 없습니다. 주문 기록은 전자상거래법에 따라
            일정 기간 보관됩니다.
          </p>
        </div>
        <label className="field">
          비밀번호 확인
          <PasswordInput
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            disabled={busy}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'withdraw-error' : undefined}
          />
        </label>
        {error && (
          <p id="withdraw-error" role="alert" className="field-error mt-0">
            {error}
          </p>
        )}
        <div className="flex justify-end border-t border-border pt-4">
          <button className="button danger" disabled={busy} aria-busy={busy}>
            {busy ? '처리 중…' : '탈퇴하기'}
          </button>
        </div>
      </div>
    </form>
  )
}
