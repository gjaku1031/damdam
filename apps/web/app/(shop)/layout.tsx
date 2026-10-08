'use client'

import { Toaster } from 'sonner'
import { SiteHeader } from '@/components/site-header'
import { SiteFooter } from '@/components/site-footer'
import { ConfirmHost } from '@/components/confirm-dialog'

export default function ShopLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-lg focus:bg-card focus:px-4 focus:py-2"
      >
        본문 바로가기
      </a>
      <SiteHeader />
      <main id="main" className="flex-1">
        {children}
      </main>
      <SiteFooter />
      <ConfirmHost />
      <Toaster
        position="top-center"
        toastOptions={{
          className: 'rounded-xl border border-border bg-card text-foreground',
        }}
      />
    </div>
  )
}
