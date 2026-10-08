import type { Metadata } from 'next'

export const metadata: Metadata = { title: '회원 관리', robots: { index: false } }

export default function Layout({ children }: { children: React.ReactNode }) {
  return children
}
