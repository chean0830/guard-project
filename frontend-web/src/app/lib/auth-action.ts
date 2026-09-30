'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { tryAdminLogin } from '@/app/lib/admin-session'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'
const SESSION_COOKIE = 'session'
const SESSION_EMAIL_COOKIE = 'session_email'

export type AuthFormState = { status: 'idle' } | { status: 'error'; message: string }

/** 로그인 후 돌아갈 주소는 우리 사이트 안의 경로(/로 시작, //는 제외)만 허용한다 — 외부 사이트로 보내는 오픈 리다이렉트 방지. */
function safeRedirectPath(path: string): string {
  return path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/\\') ? path : '/'
}

async function callAuthEndpoint(path: string, email: string, password: string) {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/auth/${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
  } catch {
    return { ok: false as const, message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }

  if (!response.ok) {
    const message = await response.text()
    return { ok: false as const, message: message || '요청에 실패했습니다.' }
  }

  const data = (await response.json()) as { token: string; email: string }
  return { ok: true as const, token: data.token, email: data.email }
}

async function setSessionCookies(token: string, email: string) {
  const cookieStore = await cookies()
  cookieStore.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24 * 7, // 7일 — 백엔드 토큰 만료 기간과 맞춤
  })
  cookieStore.set(SESSION_EMAIL_COOKIE, email, {
    httpOnly: false, // 화면에 이메일 표시용. 인증 판단에는 쓰지 않음(그건 위 httpOnly 토큰만으로 함).
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  })
}

export async function signupAction(
  redirectTo: string,
  _prevState: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const email = String(formData.get('email') ?? '')
  const password = String(formData.get('password') ?? '')

  const result = await callAuthEndpoint('signup', email, password)
  if (!result.ok) {
    return { status: 'error', message: result.message }
  }

  await setSessionCookies(result.token, result.email)
  redirect(safeRedirectPath(redirectTo))
}

export async function loginAction(
  redirectTo: string,
  _prevState: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const email = String(formData.get('email') ?? '')
  const password = String(formData.get('password') ?? '')

  const result = await callAuthEndpoint('login', email, password)
  if (!result.ok) {
    // 관리자도 이 화면에서 로그인한다 — 일반 회원이 아니면 관리자 계정인지 한 번 더 확인한다.
    if (await tryAdminLogin(email, password)) {
      redirect('/admin')
    }
    return { status: 'error', message: result.message }
  }

  await setSessionCookies(result.token, result.email)
  redirect(safeRedirectPath(redirectTo))
}

export async function logoutAction() {
  const cookieStore = await cookies()
  const token = cookieStore.get(SESSION_COOKIE)?.value

  if (token) {
    try {
      await fetch(`${BACKEND_URL}/api/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
    } catch {
      // 백엔드 호출이 실패해도 클라이언트 쿠키는 지워서 로그아웃 상태로 만든다.
    }
  }

  cookieStore.delete(SESSION_COOKIE)
  cookieStore.delete(SESSION_EMAIL_COOKIE)
  redirect('/')
}

export async function getSessionEmail(): Promise<string | null> {
  const cookieStore = await cookies()
  return cookieStore.get(SESSION_EMAIL_COOKIE)?.value ?? null
}
