import { notFound } from 'next/navigation'
import Link from 'next/link'
import { getLawyerConsultationThreadAction } from '@/app/lib/lawyer-consultation-action'
import { LawyerConsultationThreadView } from '@/app/ui/lawyer-consultation-thread'

export default async function LawyerConsultationThreadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const consultationId = Number(id)
  if (!Number.isInteger(consultationId)) {
    notFound()
  }

  const thread = await getLawyerConsultationThreadAction(consultationId)
  if (!thread) {
    notFound()
  }

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 py-8 sm:px-8">
      <Link
        href="/lawyer/consultations"
        className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300"
      >
        ← 문의함
      </Link>
      <LawyerConsultationThreadView consultationId={consultationId} initialThread={thread} />
    </div>
  )
}
