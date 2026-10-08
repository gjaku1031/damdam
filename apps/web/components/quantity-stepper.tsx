'use client'
import { Minus, Plus } from 'lucide-react'

/** 수량 조절: 줄이기·숫자·늘리기 */
export function QuantityStepper({
  value,
  min = 1,
  max,
  onChange,
  label = '수량',
  disabled = false,
}: {
  value: number
  min?: number
  max: number
  onChange: (value: number) => void
  label?: string
  disabled?: boolean
}) {
  const clamp = (n: number) => Math.max(min, Math.min(Math.max(min, max), n))
  return (
    <div
      role="group"
      aria-label={label}
      className="inline-flex items-center rounded-[10px] border border-border-strong bg-card"
    >
      <button
        type="button"
        className="flex size-11 items-center justify-center rounded-l-[10px] hover:bg-muted disabled:opacity-30"
        aria-label={label + ' 줄이기'}
        disabled={disabled || value <= min}
        onClick={() => onChange(clamp(value - 1))}
      >
        <Minus className="size-4" aria-hidden="true" />
      </button>
      <input
        className="h-11 w-12 border-x border-border bg-transparent text-center text-[15px] font-semibold outline-none focus-visible:bg-primary-soft"
        inputMode="numeric"
        aria-label={label}
        value={value}
        disabled={disabled}
        onChange={(e) => {
          const n = Number(e.target.value.replace(/\D/g, ''))
          if (n) onChange(clamp(n))
        }}
      />
      <button
        type="button"
        className="flex size-11 items-center justify-center rounded-r-[10px] hover:bg-muted disabled:opacity-30"
        aria-label={label + ' 늘리기'}
        disabled={disabled || value >= max}
        onClick={() => onChange(clamp(value + 1))}
      >
        <Plus className="size-4" aria-hidden="true" />
      </button>
    </div>
  )
}
