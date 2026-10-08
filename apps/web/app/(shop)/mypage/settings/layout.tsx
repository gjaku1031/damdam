import type { Metadata } from 'next'

export const metadata: Metadata = { title: '운영 설정', robots: { index: false } }

export default function Layout({ children }: { children: React.ReactNode }) {
  return children
}
