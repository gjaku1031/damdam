import { Suspense } from 'react'
import type { Metadata } from 'next'
import { AuthForm } from '@/components/auth-form'
import { PageLoading } from '@/components/ui-kit'

export const metadata: Metadata = { title: '회원가입', robots: { index: false } }

export default function Page() {
  return (
    <Suspense fallback={<PageLoading />}>
      <AuthForm signup />
    </Suspense>
  )
}
