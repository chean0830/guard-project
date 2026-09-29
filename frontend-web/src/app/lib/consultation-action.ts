'use server'

import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type ConsultationSummary = {
  id: number
  lawyerName: string
  lawFirm: string | null
  lastMessagePreview: string
  lastMessageAt: string
  unreadCount: number
}

export type ConsultationMessage = {
  senderType: 'USER' | 'LAWYER'
  content: string
  createdAt: string
}

export type ConsultationThread = {
  lawyerName: string
  lawFirm: string | null
  messages: ConsultationMessage[]
}

export type StartConsultationState = { status: 'idle' } | { status: 'error'; message: string }
export type SendMessageState = { status: 'idle' } | { status: 'error'; message: string }

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function startConsultationAction(
  _prevState: StartConsultationState,
  formData: FormData,
): Promise<StartConsultationState> {
  const message = String(formData.get('message') ?? '').trim()
  if (!message) {
    return { status: 'error', message: '문의 내용을 입력해주세요.' }
  }

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/consultations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ message }),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }

  if (!response.ok) {
    const text = await response.text()
    return { status: 'error', message: text || '문의 등록에 실패했습니다.' }
  }

  const data = (await response.json()) as { id: number }
  redirect(`/consult/${data.id}`)
}

export async function listConsultationsAction(): Promise<ConsultationSummary[]> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/consultations`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return []
    return (await response.json()) as ConsultationSummary[]
  } catch {
    return []
  }
}

export async function getConsultationThreadAction(id: number): Promise<ConsultationThread | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/consultations/${id}`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    return (await response.json()) as ConsultationThread
  } catch {
    return null
  }
}

export async function sendConsultationMessageAction(
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
    response = await fetch(`${BACKEND_URL}/api/consultations/${id}/messages`, {
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
