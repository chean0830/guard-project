'use server'

import { cookies } from 'next/headers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type Profile = { email: string; name: string | null; provider: string }

export type ProfileFormState = { status: 'idle' } | { status: 'error'; message: string } | { status: 'success' }

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function getProfileAction(): Promise<Profile | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/profile`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    return (await response.json()) as Profile
  } catch {
    return null
  }
}

export async function updateProfileAction(
  _prevState: ProfileFormState,
  formData: FormData,
): Promise<ProfileFormState> {
  const name = String(formData.get('name') ?? '').trim()

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/profile`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ name }),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다.' }
  }

  if (!response.ok) {
    const text = await response.text()
    return { status: 'error', message: text || '프로필 수정에 실패했습니다.' }
  }
  return { status: 'success' }
}

export async function changePasswordAction(
  _prevState: ProfileFormState,
  formData: FormData,
): Promise<ProfileFormState> {
  const currentPassword = String(formData.get('currentPassword') ?? '')
  const newPassword = String(formData.get('newPassword') ?? '')

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/profile/password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ currentPassword, newPassword }),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다.' }
  }

  if (!response.ok) {
    const text = await response.text()
    return { status: 'error', message: text || '비밀번호 변경에 실패했습니다.' }
  }
  return { status: 'success' }
}
