import Link from 'next/link'
import { listLawyerConsultationsAction } from '@/app/lib/lawyer-consultation-action'
import { LawyerConsultationList } from '@/app/ui/lawyer-consultation-list'
import { LawyerInboxLive } from '@/app/ui/inbox-live'

export default async function LawyerConsultationsPage() {
  const consultations = await listLawyerConsultationsAction()

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 py-12 sm:px-8">
      <Link href="/lawyer" className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 마이페이지
      </Link>
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">문의함</h1>
      <p className="mt-2 text-sm text-zinc-500">회원이 남긴 상담 문의 목록이에요.</p>
      <LawyerConsultationList consultations={consultations} />
      <LawyerInboxLive />
    </div>
  )
}
