'use client'

import { useEffect, useState, type ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@damdam/ui'

export type SelectOption = {
  value: string
  label: string
  description?: string
  image?: string
}

/** One accessible, styled menu for forms, filters and editor controls. */
export function AppSelect({
  value,
  onValueChange,
  options,
  label,
  placeholder = '선택하세요',
  disabled,
  className = '',
  compact = false,
  icon,
}: {
  value: string
  onValueChange: (value: string) => void
  options: SelectOption[]
  label: string
  placeholder?: string
  disabled?: boolean
  className?: string
  compact?: boolean
  icon?: ReactNode
}) {
  const selected = options.find((option) => option.value === value)
  // Radix 내부 ID가 서버·브라우저에서 달라지는 하이드레이션 경고를 피하려고, 첫 화면은 같은 모양의 버튼으로 그림
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])
  if (!mounted)
    return (
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        className={`app-select-trigger inline-flex items-center border ${compact ? 'app-select-compact' : ''} ${className}`}
      >
        {icon}
        {selected?.image && (
          <img src={selected.image} alt="" className="size-6 shrink-0 rounded object-cover" />
        )}
        <span className="min-w-0 flex-1 truncate">{selected?.label ?? placeholder}</span>
        <ChevronDown className="size-4 shrink-0 opacity-50" aria-hidden="true" />
      </button>
    )
  return (
    <Select
      value={'option:' + value}
      onValueChange={(next) => onValueChange(next.slice(7))}
      disabled={disabled}
    >
      <SelectTrigger
        aria-label={label}
        className={`app-select-trigger ${compact ? 'app-select-compact' : ''} ${className}`}
        onPointerDown={(event) => event.stopPropagation()}
      >
        {icon}
        <SelectValue placeholder={placeholder}>
          {selected?.image && (
            <img
              src={selected.image}
              alt=""
              className="size-6 shrink-0 rounded object-cover"
            />
          )}
          <span className="truncate">{selected?.label ?? placeholder}</span>
        </SelectValue>
      </SelectTrigger>
      <SelectContent
        className="app-select-menu"
        position="popper"
        sideOffset={5}
        collisionPadding={12}
      >
        {options.map((option) => (
          <SelectItem
            key={option.value}
            value={'option:' + option.value}
            textValue={option.label}
            className="app-select-option"
          >
            {option.image && (
              <img
                src={option.image}
                alt=""
                className="size-9 shrink-0 rounded object-cover"
              />
            )}
            <span className="min-w-0">
              <span className="block truncate">{option.label}</span>
              {option.description && (
                <span className="block text-xs font-normal text-muted-foreground">
                  {option.description}
                </span>
              )}
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
