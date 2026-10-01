'use server'

import { cookies } from 'next/headers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

/** 관리자가 보낸 메시지. readAt이 null이면 안 읽음. */
export type LawyerAdminMessage = { id: number; content: string; createdAt: string; readAt: string | null }

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('lawyer_session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function listLawyerAdminMessagesAction(): Promise<LawyerAdminMessage[] | null> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/admin-messages`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return null
    return (await response.json()) as LawyerAdminMessage[]
  } catch {
    return null
  }
}

export async function getLawyerAdminMessageUnreadCountAction(): Promise<number> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyer/admin-messages/unread-count`, {
      headers: await authHeader(),
      cache: 'no-store',
    })
    if (!response.ok) return 0
    return ((await response.json()) as { unreadCount: number }).unreadCount
  } catch {
    return 0
  }
}

/** 알림함을 열면 안 읽은 메시지를 모두 읽음 처리한다. */
export async function markLawyerAdminMessagesReadAction(): Promise<void> {
  try {
    await fetch(`${BACKEND_URL}/api/lawyer/admin-messages/read`, { method: 'POST', headers: await authHeader() })
  } catch {
    // 읽음 처리가 실패해도 목록은 보여준다.
  }
}
