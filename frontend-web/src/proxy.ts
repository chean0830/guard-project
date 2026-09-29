import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

/**
 * 변호사 상담(/consult/*)은 일반 회원 세션(session)을, 변호사 마이페이지(/lawyer)는
 * 별도의 변호사 세션(lawyer_session)을 요구한다 — 두 계정 체계가 서로 다른 토큰을 쓰므로
 * 게이트도 분리한다. /lawyer/login, /lawyer/signup은 로그인 전에도 접근 가능해야 하므로
 * matcher에서 제외한다.
 * (Next.js 16부터 middleware.ts가 proxy.ts로 이름이 바뀌었다 — 기능은 동일)
 */
export function proxy(request: NextRequest) {
  if (request.nextUrl.pathname === '/lawyer') {
    if (!request.cookies.has('lawyer_session')) {
      return NextResponse.redirect(new URL('/lawyer/login', request.url))
    }
    return NextResponse.next()
  }

  const hasSession = request.cookies.has('session')
  if (!hasSession) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('redirect', request.nextUrl.pathname)
    return NextResponse.redirect(loginUrl)
  }
  return NextResponse.next()
}

export const config = {
  matcher: ['/consult/:path*', '/lawyer'],
}
