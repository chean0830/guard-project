'use client'

import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { useState } from 'react'
import { createPaymentOrderAction } from '@/app/lib/payment-action'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const CLIENT_KEY = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? ''

/** 이용권이 없을 때 보여주는 결제 안내. 버튼을 누르면 서버에서 주문을 만든 뒤 토스 결제창을 연다. */
export function LawyerSelectionPayment({ price }: { price: number }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handlePay() {
    setError(null)
    if (!CLIENT_KEY) {
      setError('결제 설정이 되어 있지 않습니다. 관리자에게 문의해주세요.')
      return
    }
    setPending(true)
    const order = await createPaymentOrderAction()
    if (!order.ok) {
      setPending(false)
      setError(order.message)
      return
    }
    try {
      const tossPayments = await loadTossPayments(CLIENT_KEY)
      const payment = tossPayments.payment({ customerKey: ANONYMOUS })
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: order.amount },
        orderId: order.orderId,
        orderName: order.orderName,
        customerEmail: order.customerEmail,
        successUrl: `${window.location.origin}/api/payments/toss/success`,
        failUrl: `${window.location.origin}/consult/choose`,
      })
    } catch (e) {
      // 사용자가 결제창을 닫은 경우도 여기로 온다.
      const message = e instanceof Error ? e.message : ''
      setError(message.includes('취소') ? '결제를 취소했어요.' : message || '결제창을 열지 못했습니다.')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="rounded-2xl border border-orange-200 bg-orange-50/60 p-6 dark:border-orange-900/50 dark:bg-orange-950/20">
      <p className="text-sm font-semibold text-orange-600 dark:text-orange-400">변호사 직접 선택</p>
      <h2 className="mt-1 text-xl font-bold text-zinc-950 dark:text-zinc-50">원하는 변호사를 골라 상담하세요</h2>
      <ul className="mt-4 flex flex-col gap-1.5 text-sm text-zinc-600 dark:text-zinc-300">
        <li>· 결제하면 입점한 변호사 전체 목록(소속·전문분야·소개)을 볼 수 있어요.</li>
        <li>· 마음에 드는 변호사를 골라 바로 상담을 시작해요.</li>
        <li>· 결제 1회에 상담 1건이에요. 변호사를 고르기 전까지 이용권은 사라지지 않아요.</li>
      </ul>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <p className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">
          {price.toLocaleString('ko-KR')}원 <span className="text-sm font-normal text-zinc-500">/ 1회</span>
        </p>
        <button
          type="button"
          onClick={handlePay}
          disabled={pending}
          className="rounded-full bg-[#0064FF] px-6 py-3 text-sm font-bold text-white transition-colors hover:bg-[#0050CC] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? '결제창 여는 중...' : '토스로 결제하기'}
        </button>
      </div>

      <LoginRequiredDialog message={error} />
      {error && !isLoginRequired(error) && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}
    </div>
  )
}
