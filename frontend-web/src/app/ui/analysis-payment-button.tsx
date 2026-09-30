'use client'

import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk'
import Link from 'next/link'
import { useState } from 'react'
import { createPaymentOrderAction } from '@/app/lib/payment-action'

const CLIENT_KEY = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? ''

/**
 * 무료 분석(아이디당 5회)을 다 썼을 때 보여주는 안내. 로그인한 회원은 990원 분석 이용권을 결제하고,
 * 결제가 끝나면 이 화면으로 돌아와 다시 분석하면 이용권 1장이 쓰인다.
 */
export function AnalysisPaymentRequired({ message, loggedIn }: { message: string; loggedIn: boolean }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handlePay() {
    setError(null)
    if (!CLIENT_KEY) {
      setError('결제 설정이 되어 있지 않습니다. 관리자에게 문의해주세요.')
      return
    }
    setPending(true)
    const order = await createPaymentOrderAction('ANALYSIS')
    if (!order.ok) {
      setPending(false)
      setError(order.message)
      return
    }
    try {
      const payment = (await loadTossPayments(CLIENT_KEY)).payment({ customerKey: ANONYMOUS })
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: order.amount },
        orderId: order.orderId,
        orderName: order.orderName,
        customerEmail: order.customerEmail,
        successUrl: `${window.location.origin}/api/payments/toss/success`,
        failUrl: `${window.location.origin}/?paymentFailed=1#analyze`,
      })
    } catch (e) {
      const text = e instanceof Error ? e.message : ''
      setError(text.includes('취소') ? '결제를 취소했어요.' : text || '결제창을 열지 못했습니다.')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="mt-6 rounded-2xl border border-orange-200 bg-orange-50/60 p-5 text-left dark:border-orange-900/50 dark:bg-orange-950/20">
      <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{message}</p>
      <p className="mt-1 text-xs text-zinc-500">무료 분석은 아이디당 5회예요. 결제한 이용권은 분석이 성공했을 때만 차감되고, 쓰기 전까지 결제 내역에서 취소할 수 있어요.</p>
      {loggedIn ? (
        <button
          type="button"
          onClick={handlePay}
          disabled={pending}
          className="mt-4 rounded-full bg-[#0064FF] px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-[#0050CC] disabled:opacity-50"
        >
          {pending ? '결제창 여는 중...' : '990원 결제하고 1회 더 분석하기'}
        </button>
      ) : (
        <Link
          href="/login?redirect=%2F%23analyze"
          className="mt-4 inline-block rounded-full bg-orange-500 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600"
        >
          로그인하고 결제하기
        </Link>
      )}
      {error && <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
    </div>
  )
}
