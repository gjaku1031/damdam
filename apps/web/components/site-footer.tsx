'use client'
import Link from 'next/link'
import { useApi } from '@/hooks/use-api'
import { useAuthStore } from '@/lib/store'
import type { SiteInfo } from '@/lib/api'
import { Logo } from './ui-kit'

const policyLinks = [
  { href: '/terms', label: '이용약관' },
  { href: '/privacy', label: '개인정보처리방침', strong: true },
  { href: '/exchange-policy', label: '교환·반품 안내' },
]

export function SiteFooter() {
  const site = useApi<SiteInfo>('/site').data
  const admin = useAuthStore((s) => s.user?.role === 'ADMIN')
  // 전자상거래법 표시 사항: 운영 설정에 입력한 값만 보여줌
  const business = [
    site?.businessName && '상호 ' + site.businessName,
    site?.ownerName && '대표 ' + site.ownerName,
    site?.businessNumber && '사업자등록번호 ' + site.businessNumber,
    site?.mailOrderNumber && '통신판매업 신고 ' + site.mailOrderNumber,
  ].filter(Boolean)
  return (
    <footer className="mt-auto bg-[#24201c] text-[#d9d2c8]">
      <div className="mx-auto grid max-w-[1264px] gap-10 px-5 py-12 md:grid-cols-[2fr_1fr_1fr] md:px-8">
        <div className="flex flex-col gap-3">
          <Logo inverted />
          <p className="text-sm leading-relaxed text-[#b8aea2]">
            손으로 깎은 원목 살림을 만드는 작은 공방입니다.
          </p>
          {business.length > 0 || site?.address ? (
            <p className="text-[13px] leading-7 text-[#b8aea2]">
              {business.join(' · ')}
              {site?.address && (
                <>
                  <br />
                  {site.address}
                </>
              )}
            </p>
          ) : (
            admin && (
              <Link href="/mypage/settings" className="text-[13px] text-[#e6c9a3] underline">
                사업자 정보를 운영 설정에서 입력해주세요
              </Link>
            )
          )}
        </div>
        <div className="flex flex-col gap-2">
          <p className="text-sm font-bold text-white">고객센터</p>
          {site?.csPhone ? (
            <a href={'tel:' + site.csPhone.replace(/[^0-9+]/g, '')} className="text-lg font-bold text-white">
              {site.csPhone}
            </a>
          ) : (
            <Link href="/faq" className="text-sm text-[#d9d2c8] hover:text-white">
              자주 묻는 질문 보기
            </Link>
          )}
          {site?.csHours && <p className="text-[13px] text-[#b8aea2]">{site.csHours}</p>}
          {site?.csEmail && (
            <a href={'mailto:' + site.csEmail} className="text-[13px] text-[#b8aea2] hover:text-white">
              {site.csEmail}
            </a>
          )}
        </div>
        <nav aria-label="정책 안내" className="flex flex-col gap-2 text-sm">
          {policyLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={
                link.strong ? 'font-bold text-white' : 'text-[#d9d2c8] hover:text-white'
              }
            >
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
      <div className="border-t border-white/10 py-5 text-center text-xs text-[#9c9286]">
        © {new Date().getFullYear()} {site?.businessName || '담담공방'}
      </div>
    </footer>
  )
}
