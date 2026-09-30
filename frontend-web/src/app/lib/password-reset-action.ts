'use server'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type ResetState = { status: 'idle' } | { status: 'error'; message: string } | { status: 'success'; message: string }

async function post(path: string, body: unknown): Promise<ResetState> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/auth/password-reset/${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }
  if (!response.ok) {
    return { status: 'error', message: (await response.text()) || '요청에 실패했습니다.' }
  }
  const data = (await response.json()) as { message: string }
  return { status: 'success', message: data.message }
}

export async function requestPasswordResetAction(_prev: ResetState, formData: FormData): Promise<ResetState> {
  const email = String(formData.get('email') ?? '').trim()
  const accountType = formData.get('accountType') === 'LAWYER' ? 'LAWYER' : 'USER'
  if (!email) return { status: 'error', message: '이메일을 입력해주세요.' }
  return post('request', { email, accountType })
}

export async function confirmPasswordResetAction(token: string, _prev: ResetState, formData: FormData): Promise<ResetState> {
  const newPassword = String(formData.get('newPassword') ?? '')
  const confirm = String(formData.get('confirmPassword') ?? '')
  if (newPassword.length < 8) return { status: 'error', message: '새 비밀번호는 8자 이상이어야 합니다.' }
  if (newPassword !== confirm) return { status: 'error', message: '비밀번호 확인이 일치하지 않습니다.' }
  return post('confirm', { token, newPassword })
}
