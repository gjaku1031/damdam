'use client'
import Link from 'next/link'
import { Mail, Phone } from 'lucide-react'
import { useApi } from '@/hooks/use-api'
import type { SiteInfo } from '@/lib/api'

function useSite() {
  return useApi<SiteInfo>('/site').data
}

/** 문장 안에 넣는 고객센터 연락처. 운영 설정에 없으면 바닥글 안내로 연결 */
export function CsContactInline() {
  const site = useSite()
  const parts = [site?.csPhone && '전화 ' + site.csPhone, site?.csEmail && '이메일 ' + site.csEmail].filter(
    Boolean,
  )
  if (!parts.length) return <>페이지 하단의 고객센터 안내</>
  return <>고객센터({parts.join(' · ')})</>
}

/** 자주 묻는 질문 아래의 문의 안내 */
export function CsContactCard() {
  const site = useSite()
  return (
    <section className="card mt-10 flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h2 className="text-base font-semibold">원하는 답이 없나요?</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {site?.csHours ? site.csHours + ' 사이에 ' : ''}고객센터로 문의해주세요.
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {site?.csPhone && (
          <a href={'tel:' + site.csPhone.replace(/[^0-9+]/g, '')} className="button secondary">
            <Phone className="size-4" aria-hidden="true" />
            {site.csPhone}
          </a>
        )}
        {site?.csEmail && (
          <a href={'mailto:' + site.csEmail} className="button secondary">
            <Mail className="size-4" aria-hidden="true" />
            {site.csEmail}
          </a>
        )}
        {!site?.csPhone && !site?.csEmail && (
          <Link href="/notices" className="button secondary">
            공지사항 보기
          </Link>
        )}
      </div>
    </section>
  )
}

/** 개인정보 보호 책임자 안내: 운영 설정의 대표자·고객센터 정보를 사용 */
export function PrivacyOfficer() {
  const site = useSite()
  return (
    <div className="rounded-xl border border-border p-4">
      <p>직위: 대표{site?.ownerName ? ' · 성명: ' + site.ownerName : ''}</p>
      {site?.csPhone && <p>연락처: {site.csPhone}</p>}
      {site?.csEmail && <p>이메일: {site.csEmail}</p>}
      {!site?.csPhone && !site?.csEmail && <p>연락처: 페이지 하단의 고객센터 안내를 참고해주세요.</p>}
    </div>
  )
}
