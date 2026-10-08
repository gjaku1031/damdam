import type { ReactNode } from 'react'
import { PageHeader } from './ui-kit'

/** 약관·개인정보처리방침·교환반품 안내의 공통 틀 */
export function PolicyLayout({
  title,
  date,
  description,
  children,
}: {
  title: string
  date: string
  description?: string
  children: ReactNode
}) {
  return (
    <div className="page page-narrow">
      <PageHeader title={title} description={description ?? date} />
      {description && <p className="-mt-6 mb-8 text-sm text-muted-foreground">{date}</p>}
      <div className="card card-body flex flex-col gap-10 text-[15px] leading-7 text-foreground/85 sm:p-10 [&_h2]:mb-3 [&_h2]:text-lg [&_h2]:font-bold [&_h2]:text-foreground [&_li]:pl-1 [&_ol]:list-decimal [&_ol]:pl-5 [&_strong]:text-foreground [&_ul]:list-disc [&_ul]:pl-5">
        {children}
      </div>
    </div>
  )
}
