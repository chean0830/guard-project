'use client'

import { useEffect, useState } from 'react'
import {
  approveRefundAction,
  fetchAdminPaymentsAction,
  rejectRefundAction,
  type AdminPayment,
} from '@/app/lib/admin-moderation-action'

const TABS = [
  { value: 'REFUND_REQUESTED', label: '환불 요청' },
  { value: '', label: '전체 결제' },
  { value: 'REFUNDED', label: '환불 완료' },
  { value: 'CANCELED', label: '결제 취소' },
] as const

const STATUS_LABEL: Record<AdminPayment['payment']['status'], string> = {
  PAID: '결제 완료',
  FAILED: '결제 실패',
  CANCELED: '결제 취소',
  REFUND_REQUESTED: '환불 요청',
  REFUNDED: '환불 완료',
}

/** 관리자 결제 관리: 전체 결제 내역과, 이미 사용한 이용권의 환불 요청 승인·거절. */
export function AdminPaymentPanel({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [tab, setTab] = useState<(typeof TABS)[number]['value']>('REFUND_REQUESTED')
  const [items, setItems] = useState<AdminPayment[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  async function load(nextTab: string) {
    setLoading(true)
    setError(null)
    const result = await fetchAdminPaymentsAction(nextTab || undefined)
    setLoading(false)
    if (!result.ok) {
      setError(result.message)
      if (result.message.includes('관리자 로그인')) onUnauthorized()
      return
    }
    setItems(result.data)
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load('REFUND_REQUESTED')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleTab(next: (typeof TABS)[number]['value']) {
    setTab(next)
    await load(next)
  }

  async function handleApprove(item: AdminPayment) {
    if (!window.confirm(`${item.userEmail}의 ${item.payment.amount.toLocaleString('ko-KR')}원 결제를 환불할까요? 토스에서 결제가 취소됩니다.`)) return
    const result = await approveRefundAction(item.payment.orderId)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await load(tab)
  }

  async function handleReject(orderId: string) {
    const result = await rejectRefundAction(orderId, rejectReason)
    setRejectingId(null)
    setRejectReason('')
    if (!result.ok) {
      setError(result.message)
      return
    }
    await load(tab)
  }

  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {TABS.map((t) => (
          <button
            key={t.value || 'all'}
            onClick={() => handleTab(t.value)}
            className={`rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
              tab === t.value
                ? 'bg-orange-500 text-white'
                : 'border border-zinc-300 text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}
      {loading && <p className="mt-6 text-sm text-zinc-500">불러오는 중...</p>}
      {!loading && items.length === 0 && <p className="mt-6 text-sm text-zinc-500">해당하는 결제가 없습니다.</p>}

      <ul className="mt-6 flex flex-col gap-3">
        {items.map((item) => (
          <li key={item.payment.orderId} className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-semibold">
                {item.payment.amount.toLocaleString('ko-KR')}원 · {STATUS_LABEL[item.payment.status]}
              </p>
              <p className="text-xs text-zinc-400">
                {item.payment.paidAt ? new Date(item.payment.paidAt).toLocaleString('ko-KR') : '-'}
              </p>
            </div>
            <p className="mt-1 text-sm text-zinc-500">
              {item.userEmail} · {item.payment.consultationId !== null ? `상담 #${item.payment.consultationId}에 사용` : '미사용'}
            </p>
            <p className="text-xs text-zinc-400">주문번호 {item.payment.orderId}</p>
            {item.payment.refundReason && <p className="mt-2 text-sm">환불 사유: {item.payment.refundReason}</p>}
            {item.payment.refundRejectedReason && (
              <p className="mt-1 text-xs text-zinc-500">이전 거절 사유: {item.payment.refundRejectedReason}</p>
            )}

            {item.payment.status === 'REFUND_REQUESTED' && (
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <button
                  onClick={() => handleApprove(item)}
                  className="rounded-full bg-emerald-600 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
                >
                  환불 승인
                </button>
                {rejectingId === item.payment.orderId ? (
                  <>
                    <input
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="거절 사유 (회원에게 보여요)"
                      className="rounded-lg border border-zinc-300 bg-transparent px-3 py-1.5 text-sm dark:border-zinc-700"
                    />
                    <button
                      onClick={() => handleReject(item.payment.orderId)}
                      className="rounded-full bg-red-600 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-red-700"
                    >
                      거절 확정
                    </button>
                    <button onClick={() => setRejectingId(null)} className="text-sm text-zinc-500 hover:underline">
                      취소
                    </button>
                  </>
                ) : (
                  <button
                    onClick={() => setRejectingId(item.payment.orderId)}
                    className="rounded-full border border-red-300 px-4 py-1.5 text-sm font-semibold text-red-600 transition-colors hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950"
                  >
                    환불 거절
                  </button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
