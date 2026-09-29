import Link from 'next/link'
import { getLawyerSessionName, lawyerLogoutAction } from '@/app/lib/lawyer-auth-action'
import { listLawyerConsultationsAction } from '@/app/lib/lawyer-consultation-action'

export default async function LawyerDashboardPage() {
  const name = await getLawyerSessionName()
  const consultations = await listLawyerConsultationsAction()
  const unreadTotal = consultations.reduce((sum, c) => sum + c.unreadCount, 0)

  return (
    <div className="mx-auto flex w-full max-w-sm flex-1 flex-col items-center px-4 py-16 text-center">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">
        {name ? `${name} 변호사님, 안녕하세요` : '안녕하세요'}
      </h1>
      <p className="mt-3 text-sm text-zinc-500">가입 승인이 완료된 계정입니다.</p>

      <div className="mt-8 flex w-full flex-col gap-3">
        <Link
          href="/lawyer/consultations"
          className="flex items-center justify-between rounded-xl border border-zinc-200 px-5 py-4 text-left transition-colors hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
        >
          <span className="font-semibold text-zinc-950 dark:text-zinc-50">문의함</span>
          {unreadTotal > 0 && (
            <span className="rounded-full bg-orange-500 px-2.5 py-0.5 text-xs font-bold text-white">{unreadTotal}</span>
          )}
        </Link>
        <Link
          href="/lawyer/settings"
          className="rounded-xl border border-zinc-200 px-5 py-4 text-left font-semibold text-zinc-950 transition-colors hover:bg-zinc-50 dark:border-zinc-800 dark:text-zinc-50 dark:hover:bg-zinc-900"
        >
          설정
        </Link>
      </div>

      <form action={lawyerLogoutAction} className="mt-8">
        <button
          type="submit"
          className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          로그아웃
        </button>
      </form>
    </div>
  )
}
