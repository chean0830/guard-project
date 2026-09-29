import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

/**
 * 변호사 상담(/consult/*)만 로그인을 요구한다. 등기부등본 분석(/, /api/analyze)은
 * 이 proxy의 matcher에 포함되지 않아 로그인 없이 그대로 쓸 수 있다.
 * (Next.js 16부터 middleware.ts가 proxy.ts로 이름이 바뀌었다 — 기능은 동일)
 */
export function proxy(request: NextRequest) {
  const hasSession = request.cookies.has('session')
  if (!hasSession) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('redirect', request.nextUrl.pathname)
    return NextResponse.redirect(loginUrl)
  }
  return NextResponse.next()
}

export const config = {
  matcher: '/consult/:path*',
}
