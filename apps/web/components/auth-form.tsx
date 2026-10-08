'use client'
import { useState, type FormEvent } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { toast } from 'sonner'
import { ApiError, registerAccount } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { Logo } from './ui-kit'
import { PasswordInput } from './password-input'

export function AuthForm({ signup = false }: { signup?: boolean }) {
  const router = useRouter(),
    params = useSearchParams(),
    login = useAuthStore((s) => s.login),
    accept = useAuthStore((s) => s.acceptUser)
  const [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [confirm, setConfirm] = useState(''),
    [service, setService] = useState(false),
    [privacy, setPrivacy] = useState(false),
    [pending, setPending] = useState(false),
    [errors, setErrors] = useState<Record<string, string>>({})
  const next = params.get('next') ?? '/'
  const destination =
    next.startsWith('/') &&
    !next.startsWith('//') &&
    !next.includes('\\') &&
    !/^\/(login|signup)(\/|\?|$)/.test(next)
      ? next
      : '/'
  async function submit(event: FormEvent) {
    event.preventDefault()
    const issues: Record<string, string> = {}
    if (!/^[a-z0-9_]{4,20}$/i.test(username.trim()))
      issues.username = '영문·숫자·밑줄로 4~20자 아이디를 입력해주세요.'
    if (!signup && !password) issues.password = '비밀번호를 입력해주세요.'
    if (signup && (password.length < 12 || password.length > 128))
      issues.password = '비밀번호는 12~128자로 입력해주세요.'
    if (signup && password !== confirm) issues.confirm = '비밀번호가 일치하지 않습니다.'
    if (signup && (!service || !privacy)) issues.terms = '필수 약관에 동의해주세요.'
    setErrors(issues)
    if (Object.keys(issues).length) return
    setPending(true)
    try {
      if (signup) {
        accept(
          await registerAccount({
            username: username.trim(),
            password,
            serviceTermsAccepted: service,
            privacyTermsAccepted: privacy,
          }),
        )
        toast.success('가입을 마쳤습니다. 환영합니다!')
      } else {
        await login(username.trim(), password)
        toast.success('로그인했습니다.')
      }
      router.replace(destination)
    } catch (e) {
      if (
        e instanceof ApiError &&
        e.fields &&
        Object.keys(e.fields).some((key) =>
          ['username', 'password', 'serviceTermsAccepted', 'privacyTermsAccepted'].includes(key),
        )
      ) {
        setErrors(
          Object.fromEntries(
            Object.entries(e.fields).map(([key, message]) => [
              key.endsWith('TermsAccepted') ? 'terms' : key,
              message,
            ]),
          ),
        )
        return
      }
      setErrors({
        [e instanceof ApiError && e.status === 409 ? 'username' : 'form']:
          e instanceof Error ? e.message : '다시 시도해주세요.',
      })
    } finally {
      setPending(false)
    }
  }
  const describe = (id: string, hint?: boolean) =>
    [hint && 'hint-' + id, errors[id] && 'error-' + id].filter(Boolean).join(' ') || undefined
  const error = (id: string) =>
    errors[id] && (
      <span id={'error-' + id} className="field-error mt-0">
        {errors[id]}
      </span>
    )
  return (
    <div className="page page-narrow flex min-h-[70vh] items-center justify-center">
      <div className="card w-full max-w-[440px]">
        <div className="card-body flex flex-col gap-6 sm:p-9">
          <div className="flex flex-col items-center gap-4 text-center">
            <Link href="/" aria-label="담담공방 홈">
              <Logo />
            </Link>
            <div>
              <h1 className="text-2xl font-bold">{signup ? '회원가입' : '로그인'}</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                {signup
                  ? '아이디와 비밀번호만으로 가입합니다.'
                  : '주문 내역 확인과 댓글 작성을 위해 로그인해주세요.'}
              </p>
            </div>
          </div>
          <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
            <label className="field">
              아이디
              <input
                className="control"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                maxLength={20}
                disabled={pending}
                aria-invalid={Boolean(errors.username)}
                aria-describedby={describe('username', signup)}
              />
              {signup && (
                <span id="hint-username" className="field-hint">
                  영문·숫자·밑줄 4~20자, 대소문자 구분 없음
                </span>
              )}
              {error('username')}
            </label>
            <label className="field">
              비밀번호
              <PasswordInput
                autoComplete={signup ? 'new-password' : 'current-password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                maxLength={128}
                disabled={pending}
                aria-invalid={Boolean(errors.password)}
                aria-describedby={describe('password', signup)}
              />
              {signup && (
                <span id="hint-password" className="field-hint">
                  12~128자
                </span>
              )}
              {error('password')}
            </label>
            {signup && (
              <>
                <label className="field">
                  비밀번호 확인
                  <PasswordInput
                    autoComplete="new-password"
                    value={confirm}
                    onChange={(e) => setConfirm(e.target.value)}
                    required
                    maxLength={128}
                    disabled={pending}
                    aria-invalid={Boolean(errors.confirm)}
                    aria-describedby={describe('confirm')}
                  />
                  {error('confirm')}
                </label>
                <fieldset
                  className="flex flex-col gap-3 rounded-xl border border-border p-4 text-sm"
                  aria-describedby={errors.terms ? 'error-terms' : undefined}
                >
                  <legend className="sr-only">약관 동의</legend>
                  <label className="flex min-h-6 items-center gap-2.5 font-semibold">
                    <input
                      type="checkbox"
                      className="size-[18px]"
                      checked={service && privacy}
                      onChange={(e) => {
                        setService(e.target.checked)
                        setPrivacy(e.target.checked)
                      }}
                      disabled={pending}
                    />
                    전체 동의
                  </label>
                  <div className="h-px bg-border" />
                  {[
                    { label: '이용약관 동의 (필수)', href: '/terms', checked: service, set: setService },
                    {
                      label: '개인정보 수집·이용 동의 (필수)',
                      href: '/privacy',
                      checked: privacy,
                      set: setPrivacy,
                    },
                  ].map((term) => (
                    <div key={term.href} className="flex items-center justify-between gap-3">
                      <label className="flex min-h-6 items-center gap-2.5">
                        <input
                          type="checkbox"
                          className="size-[18px]"
                          checked={term.checked}
                          onChange={(e) => term.set(e.target.checked)}
                          disabled={pending}
                        />
                        {term.label}
                      </label>
                      <Link
                        href={term.href}
                        target="_blank"
                        className="shrink-0 text-[13px] text-muted-foreground underline underline-offset-2"
                      >
                        보기
                        <span className="sr-only"> (새 창)</span>
                      </Link>
                    </div>
                  ))}
                  {errors.terms && (
                    <span id="error-terms" className="field-error mt-0">
                      {errors.terms}
                    </span>
                  )}
                </fieldset>
              </>
            )}
            {errors.form && (
              <p role="alert" className="field-error mt-0">
                {errors.form}
              </p>
            )}
            <button className="button lg w-full" disabled={pending} aria-busy={pending}>
              {pending ? '처리 중…' : signup ? '가입하기' : '로그인'}
            </button>
          </form>
          <p className="text-center text-sm text-muted-foreground">
            {signup ? '이미 계정이 있으신가요?' : '아직 계정이 없으신가요?'}{' '}
            <Link
              className="link"
              href={(signup ? '/login' : '/signup') + '?next=' + encodeURIComponent(destination)}
            >
              {signup ? '로그인' : '회원가입'}
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
