import type { Metadata } from 'next'

export const metadata: Metadata = { title: '계정 설정', robots: { index: false } }

export default function Layout({ children }: { children: React.ReactNode }) {
  return children
}
