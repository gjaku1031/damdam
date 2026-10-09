'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { ChevronDown, LogOut, Menu, PencilLine, Search, ShoppingBag, User, X } from 'lucide-react'
import { toast } from 'sonner'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@damdam/ui'
import { useAuthStore, useCartStore } from '@/lib/store'
import { Logo } from './ui-kit'

const navLinks = [
  { href: '/products', label: '상품' },
  { href: '/posts', label: '작업 이야기' },
  { href: '/notices', label: '공지사항' },
  { href: '/faq', label: '자주 묻는 질문' },
]

const accountLinks = [
  { href: '/mypage', label: '내 계정' },
  { href: '/mypage/orders', label: '주문 내역' },
  { href: '/mypage/account', label: '계정 설정' },
]

// 세부 운영 화면은 운영 관리 안의 탭으로 이동
const adminLinks = [{ href: '/mypage', label: '운영 관리' }]

export function SiteHeader() {
  const pathname = usePathname(),
    router = useRouter()
  const user = useAuthStore((s) => s.user),
    ready = useAuthStore((s) => s.ready),
    editMode = useAuthStore((s) => s.editMode),
    toggleEditMode = useAuthStore((s) => s.toggleEditMode),
    logout = useAuthStore((s) => s.logout),
    restoreSession = useAuthStore((s) => s.restoreSession)
  const itemCount = useCartStore((s) => s.items.reduce((n, i) => n + i.quantity, 0))
  const [mobileOpen, setMobileOpen] = useState(false),
    [mounted, setMounted] = useState(false)
  const admin = user?.role === 'ADMIN'
  useEffect(() => setMounted(true), [])
  useEffect(() => {
    void restoreSession()
  }, [restoreSession])
  useEffect(() => setMobileOpen(false), [pathname])

  const isActive = (href: string) => pathname === href || pathname.startsWith(href + '/')
  async function signOut() {
    try {
      await logout()
      toast.success('로그아웃했습니다.')
      router.push('/')
    } catch {
      toast.error('로그아웃하지 못했습니다. 다시 시도해주세요.')
    }
  }
  const cartBadge = mounted && itemCount > 0 && (
    <span className="absolute right-1 top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-primary px-1 text-[11px] font-bold text-primary-foreground">
      {itemCount > 99 ? '99+' : itemCount}
    </span>
  )

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/85">
      <div className="mx-auto flex h-[var(--header-height)] max-w-[1264px] items-center justify-between gap-4 px-4 md:px-8">
        <div className="flex min-w-0 items-center gap-10">
          <Link href="/" aria-label="담담공방 홈">
            <Logo />
          </Link>
          <nav aria-label="주 메뉴" className="hidden items-center gap-7 md:flex">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                aria-current={isActive(link.href) ? 'page' : undefined}
                className={`text-[15px] font-medium transition-colors ${
                  isActive(link.href)
                    ? 'text-primary'
                    : 'text-foreground/80 hover:text-foreground'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-1">
          {admin && (
            <button
              type="button"
              aria-pressed={editMode}
              onClick={toggleEditMode}
              className={`mr-1 hidden items-center gap-1.5 rounded-full border px-3 py-1.5 text-[13px] font-semibold transition-colors sm:inline-flex ${
                editMode
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'border-border-strong bg-card text-foreground hover:border-muted-foreground'
              }`}
            >
              <PencilLine className="size-3.5" aria-hidden="true" />
              {editMode ? '편집 중' : '편집 모드'}
            </button>
          )}
          <Link href="/products?search=1" className="icon-button" aria-label="상품 검색">
            <Search className="size-5" strokeWidth={1.7} />
          </Link>
          <Link href="/cart" className="icon-button relative" aria-label={`장바구니 ${itemCount}개`}>
            <ShoppingBag className="size-5" strokeWidth={1.7} />
            {cartBadge}
          </Link>

          <div className="hidden md:block">
            {!ready ? (
              <span className="ml-2 inline-block h-9 w-20 rounded-full bg-muted" aria-hidden="true" />
            ) : user ? (
              <DropdownMenu modal={false}>
                <DropdownMenuTrigger className="ml-1 inline-flex items-center gap-1.5 rounded-full border border-border-strong bg-card py-1.5 pl-3 pr-2.5 text-sm font-semibold hover:border-muted-foreground">
                  <User className="size-4" aria-hidden="true" />
                  <span className="max-w-28 truncate">{user.username}</span>
                  <ChevronDown className="size-3.5 opacity-60" aria-hidden="true" />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" sideOffset={8} className="w-52 rounded-xl p-1.5">
                  <DropdownMenuLabel className="text-xs font-medium text-muted-foreground">
                    {admin ? '운영자' : '회원'} · {user.username}
                  </DropdownMenuLabel>
                  {(admin ? adminLinks : accountLinks).map((link) => (
                    <DropdownMenuItem key={link.href} asChild className="cursor-pointer rounded-lg px-2.5 py-2">
                      <Link href={link.href}>{link.label}</Link>
                    </DropdownMenuItem>
                  ))}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    className="cursor-pointer rounded-lg px-2.5 py-2"
                    onSelect={() => void signOut()}
                  >
                    <LogOut className="size-4" aria-hidden="true" />
                    로그아웃
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <Link
                href={'/login?next=' + encodeURIComponent(pathname)}
                className="ml-1 inline-flex min-h-9 items-center rounded-full border border-border-strong bg-card px-4 text-sm font-semibold hover:border-muted-foreground"
              >
                로그인
              </Link>
            )}
          </div>

          <button
            type="button"
            onClick={() => setMobileOpen((open) => !open)}
            className="icon-button md:hidden"
            aria-label={mobileOpen ? '메뉴 닫기' : '메뉴 열기'}
            aria-expanded={mobileOpen}
            aria-controls="mobile-menu"
          >
            {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <div id="mobile-menu" className="border-t border-border bg-background px-4 pb-6 pt-3 md:hidden">
          <nav aria-label="모바일 메뉴" className="flex flex-col">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                aria-current={isActive(link.href) ? 'page' : undefined}
                className={`rounded-lg px-3 py-3 text-base font-semibold ${
                  isActive(link.href) ? 'bg-primary-soft text-primary' : 'text-foreground'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
          <div className="my-3 h-px bg-border" />
          {user ? (
            <div className="flex flex-col">
              <p className="px-3 pb-1 text-xs font-medium text-muted-foreground">
                {admin ? '운영자' : '회원'} · {user.username}
              </p>
              {(admin ? adminLinks : accountLinks).map((link) => (
                <Link key={link.href} href={link.href} className="rounded-lg px-3 py-3 text-[15px]">
                  {link.label}
                </Link>
              ))}
              {admin && (
                <button
                  type="button"
                  aria-pressed={editMode}
                  onClick={toggleEditMode}
                  className="rounded-lg px-3 py-3 text-left text-[15px]"
                >
                  {editMode ? '편집 모드 끄기' : '편집 모드 켜기'}
                </button>
              )}
              <button
                type="button"
                onClick={() => void signOut()}
                className="rounded-lg px-3 py-3 text-left text-[15px] text-muted-foreground"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              <Link href={'/login?next=' + encodeURIComponent(pathname)} className="button">
                로그인
              </Link>
              <Link href="/signup" className="button secondary">
                회원가입
              </Link>
            </div>
          )}
        </div>
      )}
    </header>
  )
}
