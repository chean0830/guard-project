import Link from 'next/link'
import { getCreditsAction, listDirectoryLawyersAction } from '@/app/lib/payment-action'
import { LawyerDirectory } from '@/app/ui/lawyer-directory'
import { LawyerSelectionPayment } from '@/app/ui/lawyer-selection-payment'

type SearchParams = Promise<{ paid?: string; paymentError?: string; code?: string; message?: string }>

export default async function ChooseLawyerPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams
  const { credits, price } = await getCreditsAction()
  const lawyers = credits > 0 ? await listDirectoryLawyersAction() : []

  // 토스 결제창 실패 시 failUrl로 code/message가, 승인 실패 시 우리 쪽에서 paymentError가 붙어 돌아온다.
  const paymentError =
    params.paymentError ?? (params.code === 'PAY_PROCESS_CANCELED' ? '결제를 취소했어요.' : params.message)

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 py-12 sm:px-8">
      <Link href="/consult" className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 변호사 상담
      </Link>
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">원하는 변호사 선택</h1>
        <Link href="/consult/payments" className="text-sm text-zinc-500 hover:text-orange-600 hover:underline">
          결제 내역
        </Link>
      </div>

      {params.paid === '1' && credits > 0 && (
        <p className="mt-4 rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
          결제가 완료됐어요. 이제 원하는 변호사를 골라주세요.
        </p>
      )}
      {paymentError && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {paymentError}
        </p>
      )}

      <div className="mt-6">
        {credits > 0 ? <LawyerDirectory lawyers={lawyers} credits={credits} /> : <LawyerSelectionPayment price={price} />}
      </div>
    </div>
  )
}
