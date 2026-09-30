import { cookies } from 'next/headers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'
const ADMIN_SESSION_COOKIE = 'admin_session'

/**
 * 관리자 API 호출용 인증 헤더. 'use server' 파일에서 export하면 클라이언트가 호출할 수 있는
 * Server Action이 되어 httpOnly 쿠키의 토큰을 브라우저로 돌려줄 수 있으므로, 일부러 일반 서버 모듈에 둔다.
 */
export async function adminAuthHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get(ADMIN_SESSION_COOKIE)?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * 관리자 계정이면 관리자 세션 쿠키를 심고 true를 돌려준다. 관리자도 일반 로그인 화면(/login)에서
 * 로그인하므로, 일반 회원 로그인이 실패했을 때 loginAction이 이 함수로 한 번 더 확인한다.
 */
export async function tryAdminLogin(email: string, password: string): Promise<boolean> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/admin/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
  } catch {
    return false
  }
  if (!response.ok) return false

  const data = (await response.json()) as { token: string }
  const cookieStore = await cookies()
  cookieStore.set(ADMIN_SESSION_COOKIE, data.token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    path: '/',
    maxAge: 60 * 60 * 12, // 12시간 — 백엔드 관리자 토큰 만료 기간과 맞춤
  })
  return true
}

export async function clearAdminSession(): Promise<void> {
  const cookieStore = await cookies()
  const token = cookieStore.get(ADMIN_SESSION_COOKIE)?.value
  if (token) {
    try {
      await fetch(`${BACKEND_URL}/api/admin/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
    } catch {
      // 백엔드 호출이 실패해도 쿠키는 지워서 로그아웃 상태로 만든다.
    }
  }
  cookieStore.delete(ADMIN_SESSION_COOKIE)
}
