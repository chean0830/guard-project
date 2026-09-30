'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'
const LAWYER_SESSION_COOKIE = 'lawyer_session'
const LAWYER_SESSION_NAME_COOKIE = 'lawyer_session_name'

export type LawyerSignupState =
  | { status: 'idle' }
  | { status: 'error'; message: string }
  | { status: 'success'; message: string }

export type LawyerLoginState = { status: 'idle' } | { status: 'error'; message: string }

export async function lawyerSignupAction(
  _prevState: LawyerSignupState,
  formData: FormData,
): Promise<LawyerSignupState> {
  const documents = formData.getAll('documents').filter((f): f is File => f instanceof File && f.size > 0)
  if (documents.length === 0) {
    return { status: 'error', message: '변호사 자격을 확인할 수 있는 서류를 1개 이상 첨부해주세요.' }
  }

  const outgoing = new FormData()
  for (const field of ['email', 'password', 'name', 'lawFirm', 'barNumber']) {
    outgoing.append(field, String(formData.get(field) ?? ''))
  }
  for (const document of documents) {
    outgoing.append('documents', document)
  }

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/auth/signup`, {
      method: 'POST',
      body: outgoing,
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }

  if (!response.ok) {
    const message = await response.text()
    return { status: 'error', message: message || '가입 신청에 실패했습니다.' }
  }

  const data = (await response.json()) as { message: string }
  return { status: 'success', message: data.message }
}

export async function lawyerLoginAction(
  redirectTo: string,
  _prevState: LawyerLoginState,
  formData: FormData,
): Promise<LawyerLoginState> {
  const email = String(formData.get('email') ?? '')
  const password = String(formData.get('password') ?? '')

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }

  if (!response.ok) {
    const message = await response.text()
    return { status: 'error', message: message || '로그인에 실패했습니다.' }
  }

  const data = (await response.json()) as { token: string; email: string; name: string }
  const cookieStore = await cookies()
  cookieStore.set(LAWYER_SESSION_COOKIE, data.token, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  })
  cookieStore.set(LAWYER_SESSION_NAME_COOKIE, data.name, {
    httpOnly: false,
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  })
  // 로그인 후에는 변호사 화면(/lawyer...) 안에서만 돌아간다 — 외부 주소로 보내는 오픈 리다이렉트 방지.
  redirect(redirectTo.startsWith('/lawyer') && !redirectTo.startsWith('/lawyer/login') ? redirectTo : '/lawyer')
}

export async function lawyerLogoutAction() {
  const cookieStore = await cookies()
  const token = cookieStore.get(LAWYER_SESSION_COOKIE)?.value

  if (token) {
    try {
      await fetch(`${BACKEND_URL}/api/lawyer/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
    } catch {
      // 백엔드 호출이 실패해도 클라이언트 쿠키는 지워서 로그아웃 상태로 만든다.
    }
  }

  cookieStore.delete(LAWYER_SESSION_COOKIE)
  cookieStore.delete(LAWYER_SESSION_NAME_COOKIE)
  redirect('/lawyer/login')
}

export async function getLawyerSessionName(): Promise<string | null> {
  const cookieStore = await cookies()
  return cookieStore.get(LAWYER_SESSION_NAME_COOKIE)?.value ?? null
}
