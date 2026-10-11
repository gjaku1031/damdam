import type { Metadata, Viewport } from 'next'
import localFont from 'next/font/local'
import './globals.css'

const pretendard = localFont({
  src: './fonts/PretendardVariable.woff2',
  variable: '--font-pretendard',
  weight: '45 920',
  display: 'swap',
})

const description =
  '도마·트레이·수납함처럼 매일 쓰는 물건을 원목으로 손수 만드는 담담공방입니다.'

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'https://damdamwood.co.kr'),
  title: { default: '담담공방 · 손으로 깎은 원목 살림', template: '%s · 담담공방' },
  description,
  openGraph: {
    type: 'website',
    locale: 'ko_KR',
    siteName: '담담공방',
    title: '담담공방 · 손으로 깎은 원목 살림',
    description,
    images: [{ url: '/brand/hero-workshop.jpg', width: 1200, height: 630, alt: '담담공방 작업실' }],
  },
  icons: {
    // 16·32·48px을 크기별로 다듬어 담은 파일, 탭에서 선이 뭉개지지 않게 SVG 대신 이것만 연결
    icon: [{ url: '/favicon.ico', sizes: '16x16 32x32 48x48' }],
    apple: '/apple-icon.png',
  },
}

export const viewport: Viewport = {
  themeColor: '#f8f6f2',
  userScalable: true,
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko" className={pretendard.variable} suppressHydrationWarning>
      <body className="font-sans antialiased" suppressHydrationWarning>
        {children}
      </body>
    </html>
  )
}
