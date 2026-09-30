'use server'

import { cookies } from 'next/headers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type LawyerProfile = {
  email: string
  name: string
  lawFirm: string | null
  barNumber: string
  specialties: string | null
  introduction: string | null
  emailNotificationsEnabled: boolean
  status: string
  headline: string | null
  careerYears: number | null
  feeInfo: string | null
  achievements: string | null
}

export type ProfileFormState = { status: 'idle' } | { status: 'error'; message: string } | { status: 'success' }

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('lawyer_session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function getLawyerProfileAction(): Promise<LawyerProfile | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/profile`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    return (await response.json()) as LawyerProfile
  } catch {
    return null
  }
}

export async function updateLawyerProfileAction(
  _prevState: ProfileFormState,
  formData: FormData,
): Promise<ProfileFormState> {
  const name = String(formData.get('name') ?? '').trim()
  if (!name) {
    return { status: 'error', message: '이름을 입력해주세요.' }
  }

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/profile`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({
        name,
        lawFirm: String(formData.get('lawFirm') ?? '') || null,
        specialties: String(formData.get('specialties') ?? '') || null,
        introduction: String(formData.get('introduction') ?? '') || null,
        headline: String(formData.get('headline') ?? '') || null,
        careerYears: String(formData.get('careerYears') ?? '') === '' ? null : Number(formData.get('careerYears')),
        feeInfo: String(formData.get('feeInfo') ?? '') || null,
        achievements: String(formData.get('achievements') ?? '') || null,
      }),
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

export async function updateLawyerNotificationSettingAction(enabled: boolean): Promise<void> {
  try {
    await fetch(`${BACKEND_URL}/api/lawyer/profile/notifications`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ enabled }),
    })
  } catch {
    // 알림 설정 저장 실패는 조용히 무시 — 다음 저장 시도에서 다시 반영됨
  }
}

export async function changeLawyerPasswordAction(
  _prevState: ProfileFormState,
  formData: FormData,
): Promise<ProfileFormState> {
  const currentPassword = String(formData.get('currentPassword') ?? '')
  const newPassword = String(formData.get('newPassword') ?? '')

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/profile/password`, {
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
