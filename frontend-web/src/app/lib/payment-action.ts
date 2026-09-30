'use server'

import { cookies } from 'next/headers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export type PaymentOrder = { orderId: string; amount: number; orderName: string }
export type Credits = { credits: number; price: number }
export type DirectoryLawyer = {
  id: number
  name: string
  lawFirm: string | null
  specialties: string | null
  introduction: string | null
  headline: string | null
  careerYears: number | null
  feeInfo: string | null
  achievements: string | null
}

export type CreateOrderResult = ({ ok: true } & PaymentOrder & { customerEmail: string | null }) | { ok: false; message: string }
export type StartDirectState =
  | { status: 'idle' }
  | { status: 'error'; message: string }
  | { status: 'success'; consultationId: number }

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 결제창을 열기 전에 서버에서 주문을 만든다 — 금액·주문번호는 항상 서버가 정한다. */
export async function createPaymentOrderAction(): Promise<CreateOrderResult> {
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/payments/orders`, {
      method: 'POST',
      headers: await authHeader(),
      cache: 'no-store',
    })
  } catch {
    return { ok: false, message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }
  if (!response.ok) {
    return { ok: false, message: (await response.text()) || '주문을 만들지 못했습니다.' }
  }
  const order = (await response.json()) as PaymentOrder
  const cookieStore = await cookies()
  return { ok: true, ...order, customerEmail: cookieStore.get('session_email')?.value ?? null }
}

export async function getCreditsAction(): Promise<Credits> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/payments/credits`, { headers: await authHeader(), cache: 'no-store' })
    if (response.ok) return (await response.json()) as Credits
  } catch {
    // 조회 실패 시 이용권 없음으로 보고 결제 안내를 보여준다.
  }
  return { credits: 0, price: 2900 }
}

export async function listDirectoryLawyersAction(): Promise<DirectoryLawyer[]> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/lawyers/directory`, { headers: await authHeader(), cache: 'no-store' })
    if (response.ok) return (await response.json()) as DirectoryLawyer[]
  } catch {
    // 아래에서 빈 목록 처리
  }
  return []
}

export async function startDirectConsultationAction(
  lawyerId: number,
  _prevState: StartDirectState,
  formData: FormData,
): Promise<StartDirectState> {
  const message = String(formData.get('message') ?? '').trim()
  if (!message) {
    return { status: 'error', message: '문의 내용을 입력해주세요.' }
  }

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/consultations/direct`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body: JSON.stringify({ lawyerId, message }),
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }
  if (!response.ok) {
    return { status: 'error', message: (await response.text()) || '문의 등록에 실패했습니다.' }
  }
  const data = (await response.json()) as { id: number }
  return { status: 'success', consultationId: data.id }
}
