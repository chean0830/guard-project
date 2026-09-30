import Link from 'next/link'
import { getPaymentHistoryAction } from '@/app/lib/payment-action'
import { PaymentHistory } from '@/app/ui/payment-history'

export default async function PaymentHistoryPage() {
  const items = await getPaymentHistoryAction()

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 py-12 sm:px-8">
      <Link href="/consult" className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 변호사 상담
      </Link>
      <h1 className="mb-6 text-2xl font-bold text-zinc-950 dark:text-zinc-50">결제 내역</h1>
      <PaymentHistory initialItems={items} />
    </div>
  )
}
