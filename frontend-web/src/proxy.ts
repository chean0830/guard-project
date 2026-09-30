import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

/**
 * 변호사 상담(/consult/*)·설정(/settings)은 일반 회원 세션(session)을, 변호사 마이페이지
 * (/lawyer/*)는 별도의 변호사 세션(lawyer_session)을 요구한다 — 두 계정 체계가 서로 다른
 * 토큰을 쓰므로 게이트도 분리한다. /lawyer/login, /lawyer/signup은 로그인 전에도 접근
 * 가능해야 하므로 이 두 경로만 명시적으로 예외 처리한다. 관리자 페이지(/admin/*)는 또 다른
 * 관리자 세션(admin_session)을 요구한다. 관리자도 일반 로그인 화면(/login)에서 로그인한다.
 * (Next.js 16부터 middleware.ts가 proxy.ts로 이름이 바뀌었다 — 기능은 동일)
 */
const LAWYER_PUBLIC_PATHS = ['/lawyer/login', '/lawyer/signup']
const ADMIN_PUBLIC_PATHS = ['/admin/login']

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl

  if (pathname.startsWith('/admin')) {
    if (ADMIN_PUBLIC_PATHS.includes(pathname)) {
      return NextResponse.next()
    }
    if (!request.cookies.has('admin_session')) {
      const loginUrl = new URL('/login', request.url)
      loginUrl.searchParams.set('redirect', '/admin')
      return NextResponse.redirect(loginUrl)
    }
    return NextResponse.next()
  }

  if (pathname.startsWith('/lawyer')) {
    if (LAWYER_PUBLIC_PATHS.includes(pathname)) {
      return NextResponse.next()
    }
    if (!request.cookies.has('lawyer_session')) {
      return NextResponse.redirect(new URL('/lawyer/login', request.url))
    }
    return NextResponse.next()
  }

  const hasSession = request.cookies.has('session')
  if (!hasSession) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('redirect', pathname)
    return NextResponse.redirect(loginUrl)
  }
  return NextResponse.next()
}

export const config = {
  matcher: ['/consult/:path*', '/settings/:path*', '/lawyer/:path*', '/admin', '/admin/:path*'],
}
