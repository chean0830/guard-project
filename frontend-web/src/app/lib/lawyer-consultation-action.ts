'use server'

import { cookies } from 'next/headers'
import type { BlockedEntry, BlockResult } from '@/app/lib/block-types'
import type { ReportReason, ReportResult } from '@/app/lib/report-types'

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
  counterpartBlocked: boolean
  blockState: 'NONE' | 'BLOCKED_BY_ME' | 'BLOCKED_ME'
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

export async function reportLawyerConsultationAction(id: number, reason: ReportReason, detail: string): Promise<ReportResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/consultations/${id}/report`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ reason, detail }),
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }

  if (!response.ok) {
    const text = await response.text()
    return { ok: false, message: text || '신고 접수에 실패했습니다.' }
  }

  const data = (await response.json()) as { message: string }
  return { ok: true, message: data.message }
}

export async function getLawyerConsultationSocketTicketAction(id: number): Promise<string | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/consultations/${id}/socket-ticket`, {
      method: 'POST',
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    const data = (await response.json()) as { ticket: string }
    return data.ticket
  } catch {
    return null
  }
}

/** 변호사 문의함 실시간 알림(/ws/lawyer-inbox) 입장권. */
export async function getLawyerInboxSocketTicketAction(): Promise<string | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/inbox/socket-ticket`, {
      method: 'POST',
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    const data = (await response.json()) as { ticket: string }
    return data.ticket
  } catch {
    return null
  }
}

export async function blockLawyerConsultationCounterpartAction(id: number): Promise<BlockResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/consultations/${id}/block`, { method: 'POST', headers: await authHeader() })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) return { ok: false, message: (await response.text()) || '차단하지 못했습니다.' }
  return { ok: true }
}

export async function listLawyerBlocksAction(): Promise<BlockedEntry[]> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/blocks`, { headers: await authHeader(), cache: 'no-store' })
    if (!response.ok) return []
    return (await response.json()) as BlockedEntry[]
  } catch {
    return []
  }
}

export async function lawyerUnblockAction(blockId: number): Promise<BlockResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/lawyer/blocks/${blockId}`, { method: 'DELETE', headers: await authHeader() })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) return { ok: false, message: (await response.text()) || '차단을 해제하지 못했습니다.' }
  return { ok: true }
}
