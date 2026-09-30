'use server'

import { adminAuthHeader } from '@/app/lib/admin-session'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type ReportStatus = 'PENDING' | 'ACTIONED' | 'DISMISSED'
export type PartyType = 'USER' | 'LAWYER'

export type AdminReport = {
  id: number
  consultationId: number
  reason: 'ABUSIVE_LANGUAGE' | 'MONEY_REQUEST'
  reasonLabel: string
  detail: string | null
  status: ReportStatus
  createdAt: string
  resolvedAt: string | null
  reporterType: PartyType
  reporterName: string
  targetType: PartyType
  targetId: number
  targetName: string
  targetEmail: string | null
  targetBlocked: boolean
  targetReportCount: number
}

export type AdminReportMessage = { senderType: PartyType; content: string; createdAt: string }

export type AdminUserMember = {
  id: number
  email: string
  name: string | null
  provider: string
  blocked: boolean
  blockedReason: string | null
  blockedAt: string | null
  reportCount: number
}

export type AdminLawyerMember = {
  id: number
  email: string
  name: string
  lawFirm: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  blocked: boolean
  blockedReason: string | null
  blockedAt: string | null
  reportCount: number
}

export type AdminResult<T> = { ok: true; data: T } | { ok: false; message: string }

async function adminFetch<T>(path: string, init?: { method?: string; body?: unknown }): Promise<AdminResult<T>> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/admin${path}`, {
      method: init?.method ?? 'GET',
      headers: {
        ...(await adminAuthHeader()),
        ...(init?.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
      cache: 'no-store',
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) {
    if (response.status === 401) return { ok: false, message: '관리자 로그인이 필요합니다.' }
    const text = await response.text()
    return { ok: false, message: text || `요청에 실패했습니다. (${response.status})` }
  }
  const text = await response.text()
  return { ok: true, data: (text ? JSON.parse(text) : null) as T }
}

export async function fetchReportsAction(status?: ReportStatus) {
  return adminFetch<AdminReport[]>(`/reports${status ? `?status=${status}` : ''}`)
}

export async function fetchReportMessagesAction(reportId: number) {
  return adminFetch<AdminReportMessage[]>(`/reports/${reportId}/messages`)
}

export async function actionReportAction(reportId: number) {
  return adminFetch<null>(`/reports/${reportId}/action`, { method: 'POST' })
}

export async function dismissReportAction(reportId: number) {
  return adminFetch<null>(`/reports/${reportId}/dismiss`, { method: 'POST' })
}

export type MemberPage<T> = { items: T[]; page: number; totalPages: number; totalElements: number }

export async function fetchMembersAction(type: PartyType, page = 0, q = '') {
  const query = `?page=${page}${q ? `&q=${encodeURIComponent(q)}` : ''}`
  return type === 'USER'
    ? adminFetch<MemberPage<AdminUserMember>>(`/members/users${query}`)
    : adminFetch<MemberPage<AdminLawyerMember>>(`/members/lawyers${query}`)
}

export async function setMemberBlockedAction(type: PartyType, id: number, blocked: boolean, reason?: string) {
  const segment = type === 'USER' ? 'users' : 'lawyers'
  return blocked
    ? adminFetch<null>(`/members/${segment}/${id}/block`, { method: 'POST', body: { reason: reason ?? '' } })
    : adminFetch<null>(`/members/${segment}/${id}/unblock`, { method: 'POST' })
}

export type AdminPayment = {
  payment: {
    orderId: string
    productType: 'LAWYER_SELECTION' | 'ANALYSIS'
    used: boolean
    orderName: string
    amount: number
    status: 'PAID' | 'FAILED' | 'CANCELED' | 'REFUND_REQUESTED' | 'REFUNDED'
    paidAt: string | null
    canceledAt: string | null
    consultationId: number | null
    refundReason: string | null
    refundRejectedReason: string | null
  }
  userId: number
  userEmail: string
}

export async function fetchAdminPaymentsAction(status?: string) {
  return adminFetch<AdminPayment[]>(`/payments${status ? `?status=${status}` : ''}`)
}

export async function approveRefundAction(orderId: string) {
  return adminFetch<null>(`/payments/${encodeURIComponent(orderId)}/refund/approve`, { method: 'POST' })
}

export async function rejectRefundAction(orderId: string, reason: string) {
  return adminFetch<null>(`/payments/${encodeURIComponent(orderId)}/refund/reject`, { method: 'POST', body: { reason } })
}
