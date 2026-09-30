'use client'

import Link from 'next/link'
import { useState } from 'react'
import {
  cancelPaymentAction,
  getPaymentHistoryAction,
  requestRefundAction,
  type PaymentHistoryItem,
} from '@/app/lib/payment-action'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const STATUS: Record<PaymentHistoryItem['status'], { label: string; className: string }> = {
  PAID: { label: '결제 완료', className: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' },
  FAILED: { label: '결제 실패', className: 'bg-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300' },
  CANCELED: { label: '결제 취소', className: 'bg-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300' },
  REFUND_REQUESTED: { label: '환불 검토 중', className: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400' },
  REFUNDED: { label: '환불 완료', className: 'bg-sky-100 text-sky-700 dark:bg-sky-500/10 dark:text-sky-400' },
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : '-'
}

/**
 * 결제 내역. 쓰지 않은 이용권은 바로 결제 취소, 이미 상담에 쓴 이용권은 환불 요청(관리자 승인 후 환불)만 할 수 있다.
 */
export function PaymentHistory({ initialItems }: { initialItems: PaymentHistoryItem[] }) {
  const [items, setItems] = useState(initialItems)
  const [error, setError] = useState<string | null>(null)
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [refundingId, setRefundingId] = useState<string | null>(null)
  const [refundReason, setRefundReason] = useState('')

  async function reload() {
    setItems(await getPaymentHistoryAction())
  }

  async function handleCancel(item: PaymentHistoryItem) {
    if (!window.confirm(`${item.amount.toLocaleString('ko-KR')}원 결제를 취소할까요? 이용권 1장이 사라지고 결제 금액이 돌려받아져요.`)) return
    setError(null)
    setPendingId(item.orderId)
    const result = await cancelPaymentAction(item.orderId)
    setPendingId(null)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await reload()
  }

  async function handleRefund(item: PaymentHistoryItem) {
    setError(null)
    setPendingId(item.orderId)
    const result = await requestRefundAction(item.orderId, refundReason)
    setPendingId(null)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setRefundingId(null)
    setRefundReason('')
    await reload()
  }

  return (
    <div>
      <p className="rounded-xl bg-zinc-50 p-4 text-xs leading-relaxed text-zinc-500 dark:bg-zinc-900">
        · 변호사를 고르지 않은(쓰지 않은) 이용권은 <strong className="text-zinc-700 dark:text-zinc-300">바로 결제 취소</strong>할 수 있어요.
        <br />· 이미 상담에 쓴 이용권은 <strong className="text-zinc-700 dark:text-zinc-300">환불 요청</strong> 후 관리자가 확인해 승인하면 환불돼요.
      </p>

      {error && !isLoginRequired(error) && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}
      <LoginRequiredDialog message={error} />

      {items.length === 0 ? (
        <p className="mt-6 rounded-xl border border-dashed border-zinc-300 p-6 text-center text-sm text-zinc-400 dark:border-zinc-700">
          결제 내역이 없어요.
        </p>
      ) : (
        <ul className="mt-6 flex flex-col gap-3">
          {items.map((item) => {
            const unused = item.status === 'PAID' && item.consultationId === null
            const used = item.status === 'PAID' && item.consultationId !== null
            return (
              <li key={item.orderId} className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-semibold text-zinc-950 dark:text-zinc-50">
                    {item.amount.toLocaleString('ko-KR')}원
                    <span className={`ml-2 rounded-full px-2 py-0.5 text-[11px] font-semibold ${STATUS[item.status].className}`}>
                      {STATUS[item.status].label}
                    </span>
                  </p>
                  <p className="text-xs text-zinc-400">{formatDate(item.paidAt)}</p>
                </div>
                <p className="mt-1 text-sm text-zinc-500">
                  {item.orderName} ·{' '}
                  {item.consultationId !== null ? (
                    <Link href={`/consult/${item.consultationId}`} className="text-orange-600 hover:underline dark:text-orange-400">
                      사용한 상담 보기
                    </Link>
                  ) : item.status === 'PAID' ? (
                    <Link href="/consult/choose" className="text-orange-600 hover:underline dark:text-orange-400">
                      미사용 · 변호사 고르러 가기
                    </Link>
                  ) : (
                    '미사용'
                  )}
                </p>
                <p className="mt-1 text-xs text-zinc-400">주문번호 {item.orderId}</p>

                {item.refundReason && (item.status === 'REFUND_REQUESTED' || item.status === 'REFUNDED') && (
                  <p className="mt-2 text-xs text-zinc-500">환불 사유: {item.refundReason}</p>
                )}
                {item.refundRejectedReason && item.status === 'PAID' && (
                  <p className="mt-2 text-xs text-red-600 dark:text-red-400">환불 요청이 거절됐어요: {item.refundRejectedReason}</p>
                )}
                {(item.status === 'CANCELED' || item.status === 'REFUNDED') && (
                  <p className="mt-2 text-xs text-zinc-500">{formatDate(item.canceledAt)}에 결제가 취소됐어요.</p>
                )}

                {unused && (
                  <button
                    type="button"
                    onClick={() => handleCancel(item)}
                    disabled={pendingId === item.orderId}
                    className="mt-3 rounded-full border border-zinc-300 px-4 py-1.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 disabled:opacity-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
                  >
                    {pendingId === item.orderId ? '취소 중...' : '결제 취소'}
                  </button>
                )}
                {used &&
                  (refundingId === item.orderId ? (
                    <div className="mt-3 flex flex-col gap-2">
                      <textarea
                        value={refundReason}
                        onChange={(e) => setRefundReason(e.target.value)}
                        maxLength={500}
                        rows={3}
                        placeholder="환불을 요청하는 이유를 적어주세요. (예: 변호사 답변을 받지 못했어요)"
                        className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none focus:border-orange-400 dark:border-zinc-700"
                      />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => handleRefund(item)}
                          disabled={pendingId === item.orderId || !refundReason.trim()}
                          className="rounded-full bg-orange-500 px-4 py-1.5 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:opacity-50"
                        >
                          {pendingId === item.orderId ? '요청 중...' : '환불 요청 보내기'}
                        </button>
                        <button type="button" onClick={() => setRefundingId(null)} className="px-3 text-sm text-zinc-500 hover:underline">
                          취소
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setRefundingId(item.orderId)}
                      className="mt-3 rounded-full border border-zinc-300 px-4 py-1.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
                    >
                      환불 요청
                    </button>
                  ))}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
