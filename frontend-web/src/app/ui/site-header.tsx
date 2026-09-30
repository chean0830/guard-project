'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { adminLogoutAction } from '@/app/lib/admin-auth-action'
import { logoutAction } from '@/app/lib/auth-action'
import { lawyerLogoutAction } from '@/app/lib/lawyer-auth-action'

export type HeaderSessions = {
  userEmail: string | null
  lawyerName: string | null
  isAdmin: boolean
}

// 채팅 화면은 입력창과 대화에 집중하도록 머리글을 숨긴다.
const CHAT_PATHS = [/^\/consult\/\d+$/, /^\/lawyer\/consultations\/\d+$/]
const LOGIN_PATHS = ['/login', '/signup', '/lawyer/login', '/lawyer/signup', '/forgot-password', '/reset-password']

const linkStyle = 'text-zinc-500 transition-colors hover:text-zinc-800 dark:hover:text-zinc-200'
const logoutStyle = 'font-semibold text-orange-600 hover:underline dark:text-orange-400'

type Area = 'user' | 'lawyer' | 'admin'

/** 지금 보고 있는 화면에 맞는 계정을 고른다 — 한 브라우저에 회원·변호사·관리자 세션이 같이 있을 수 있어서. */
function pickArea(pathname: string, sessions: HeaderSessions): Area | null {
  if (pathname.startsWith('/admin') && sessions.isAdmin) return 'admin'
  if (pathname.startsWith('/lawyer') && sessions.lawyerName) return 'lawyer'
  if (sessions.userEmail) return 'user'
  if (sessions.lawyerName) return 'lawyer'
  if (sessions.isAdmin) return 'admin'
  return null
}

/** 모든 화면(채팅 제외) 위에 붙는 머리글. 로그인한 동안에는 어디서든 바로 로그아웃할 수 있다. */
export function SiteHeader({ sessions }: { sessions: HeaderSessions }) {
  const pathname = usePathname()
  if (CHAT_PATHS.some((re) => re.test(pathname))) return null

  const area = pickArea(pathname, sessions)

  return (
    <header className="border-b border-zinc-100 bg-white/90 backdrop-blur dark:border-zinc-900 dark:bg-zinc-950/90">
      <div className="mx-auto flex h-12 max-w-5xl items-center justify-between gap-4 px-4 text-sm sm:px-8">
        <Link href="/" className="font-bold text-zinc-950 dark:text-zinc-50">
          Project Guard
        </Link>

        <nav className="flex min-w-0 items-center gap-4">
          {area === 'user' && (
            <>
              <Link href="/consult" className={linkStyle}>
                내 문의
              </Link>
              <Link href="/settings" className={linkStyle}>
                설정
              </Link>
              <span className="hidden truncate text-zinc-400 sm:inline">{sessions.userEmail}님</span>
              <form action={logoutAction}>
                <button type="submit" className={logoutStyle}>
                  로그아웃
                </button>
              </form>
            </>
          )}
          {area === 'lawyer' && (
            <>
              <Link href="/lawyer/consultations" className={linkStyle}>
                문의함
              </Link>
              <Link href="/lawyer/settings" className={linkStyle}>
                설정
              </Link>
              <span className="hidden truncate text-zinc-400 sm:inline">{sessions.lawyerName?.endsWith('변호사') ? sessions.lawyerName : `${sessions.lawyerName} 변호사`}님</span>
              <form action={lawyerLogoutAction}>
                <button type="submit" className={logoutStyle}>
                  로그아웃
                </button>
              </form>
            </>
          )}
          {area === 'admin' && (
            <>
              <Link href="/admin" className={linkStyle}>
                관리자 페이지
              </Link>
              <form action={adminLogoutAction}>
                <button type="submit" className={logoutStyle}>
                  로그아웃
                </button>
              </form>
            </>
          )}
          {area === null && !LOGIN_PATHS.includes(pathname) && (
            <Link href="/login" className={logoutStyle}>
              로그인
            </Link>
          )}
        </nav>
      </div>
    </header>
  )
}
