'use client'
import { useState, type InputHTMLAttributes } from 'react'
import { Check, Circle, Eye, EyeOff } from 'lucide-react'

/** 새 비밀번호가 채워야 하는 조건, 서버의 AuthRules.PASSWORD와 같음 */
export const PASSWORD_RULES = [
  { label: '영문 포함', test: (value: string) => /[A-Za-z]/.test(value) },
  { label: '숫자 포함', test: (value: string) => /[0-9]/.test(value) },
  { label: '8자 이상', test: (value: string) => value.length >= 8 },
]

/** 조건을 채우지 못했을 때의 안내, 서버 오류 메시지와 같음 */
export const PASSWORD_MESSAGE = '비밀번호는 영문과 숫자를 포함해 8자 이상으로 입력해주세요.'

/** 새 비밀번호로 쓸 수 있는지 확인, 128자 상한은 입력란의 maxLength로도 막음 */
export const isValidPassword = (value: string) =>
  value.length <= 128 && PASSWORD_RULES.every((rule) => rule.test(value))

/** 입력하는 동안 조건별 충족 여부를 보여주는 목록 */
export function PasswordChecklist({ id, value }: { id: string; value: string }) {
  return (
    <span id={id} role="list" className="field-hint flex flex-wrap gap-x-4 gap-y-1">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.test(value)
        return (
          <span
            key={rule.label}
            role="listitem"
            className={`flex items-center gap-1 ${met ? 'font-semibold text-foreground' : ''}`}
          >
            {met ? <Check className="size-3.5" aria-hidden /> : <Circle className="size-3.5" aria-hidden />}
            {rule.label}
            <span className="sr-only">{met ? ' 충족' : ' 미충족'}</span>
          </span>
        )
      })}
    </span>
  )
}

/** 보기·숨기기 버튼이 있는 비밀번호 입력 */
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [visible, setVisible] = useState(false)
  return (
    <span className="relative block">
      <input {...props} type={visible ? 'text' : 'password'} className={`control pr-12 ${props.className ?? ''}`} />
      <button
        type="button"
        className="absolute right-1 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
        aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}
        aria-pressed={visible}
        onClick={() => setVisible((v) => !v)}
        disabled={props.disabled}
      >
        {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </span>
  )
}
