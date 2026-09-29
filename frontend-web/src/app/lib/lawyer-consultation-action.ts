'use server'

import { cookies } from 'next/headers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type LawyerConsultationSummary = {
  id: number
  userDisplayName: string
  lastMessagePreview: string
  lastMessageAt: string
  unreadCount: number
}

export type ConsultationMessage = {
  senderType: 'USER' | 'LAWYER'
  content: string
  createdAt: string
}

export type LawyerConsultationThread = {
  userDisplayName: string
  messages: ConsultationMessage[]
}

export type SendMessageState = { status: 'idle' } | { status: 'error'; message: string }

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('lawyer_session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function listLawyerConsultationsAction(): Promise<LawyerConsultationSummary[]> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/consultations`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return []
    return (await response.json()) as LawyerConsultationSummary[]
  } catch {
    return []
  }
}

export async function getLawyerConsultationThreadAction(id: number): Promise<LawyerConsultationThread | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/consultations/${id}`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    return (await response.json()) as LawyerConsultationThread
  } catch {
    return null
  }
}

export async function sendLawyerMessageAction(
  id: number,
  _prevState: SendMessageState,
  formData: FormData,
): Promise<SendMessageState> {
  const content = String(formData.get('content') ?? '').trim()
  if (!content) {
    return { status: 'error', message: '메시지를 입력해주세요.' }
  }

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/consultations/${id}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ content }),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다.' }
  }

  if (!response.ok) {
    const text = await response.text()
    return { status: 'error', message: text || '메시지 전송에 실패했습니다.' }
  }

  return { status: 'idle' }
}
