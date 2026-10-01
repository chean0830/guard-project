'use server'

import { adminAuthHeader } from '@/app/lib/admin-session'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type LawyerDocumentSummary = { id: number; fileName: string; contentType: string }

export type LawyerApplicant = {
  id: number
  email: string
  name: string
  lawFirm: string | null
  barNumber: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectionReason: string | null
  createdAt: string
  documents: LawyerDocumentSummary[]
}

export type AdminListResult =
  | { ok: true; lawyers: LawyerApplicant[] }
  | { ok: false; message: string }

export type AdminActionResult = { ok: true } | { ok: false; message: string }

export type AdminDocumentResult =
  | { ok: true; fileName: string; contentType: string; base64: string }
  | { ok: false; message: string }

async function readErrorMessage(response: Response): Promise<string> {
  const text = await response.text()
  return text || `요청에 실패했습니다. (${response.status})`
}

export async function fetchLawyerApplicantsAction(status?: string): Promise<AdminListResult> {
  const url = new URL(`${BACKEND_URL}/api/admin/lawyers`)
  if (status) url.searchParams.set('status', status)

  let response: Response
  try {
    response = await fetch(url, { headers: await adminAuthHeader(), cache: 'no-store' })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) {
    return { ok: false, message: response.status === 401 ? '관리자 로그인이 필요합니다.' : await readErrorMessage(response) }
  }
  const lawyers = (await response.json()) as LawyerApplicant[]
  return { ok: true, lawyers }
}

export async function approveLawyerAction(lawyerId: number): Promise<AdminActionResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/admin/lawyers/${lawyerId}/approve`, {
      method: 'POST',
      headers: await adminAuthHeader(),
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) {
    return { ok: false, message: await readErrorMessage(response) }
  }
  return { ok: true }
}

/** 승인 취소 → 승인 대기로 돌아가고 그 변호사의 로그인이 모두 끊긴다. */
export async function revokeLawyerAction(lawyerId: number): Promise<AdminActionResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/admin/lawyers/${lawyerId}/revoke`, {
      method: 'POST',
      headers: await adminAuthHeader(),
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) {
    return { ok: false, message: await readErrorMessage(response) }
  }
  return { ok: true }
}

export async function rejectLawyerAction(lawyerId: number, reason: string): Promise<AdminActionResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/admin/lawyers/${lawyerId}/reject`, {
      method: 'POST',
      headers: { ...(await adminAuthHeader()), 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) {
    return { ok: false, message: await readErrorMessage(response) }
  }
  return { ok: true }
}

export async function downloadLawyerDocumentAction(
  lawyerId: number,
  documentId: number,
): Promise<AdminDocumentResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/admin/lawyers/${lawyerId}/documents/${documentId}`, {
      headers: await adminAuthHeader(),
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다.' }
  }
  if (!response.ok) {
    return { ok: false, message: await readErrorMessage(response) }
  }
  const contentType = response.headers.get('Content-Type') ?? 'application/octet-stream'
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const fileNameMatch = disposition.match(/filename="(.+)"/)
  const fileName = fileNameMatch ? fileNameMatch[1] : `document-${documentId}`

  const buffer = Buffer.from(await response.arrayBuffer())
  return { ok: true, fileName, contentType, base64: buffer.toString('base64') }
}
