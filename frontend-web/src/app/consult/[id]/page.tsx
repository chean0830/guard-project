import { notFound } from 'next/navigation'
import Link from 'next/link'
import { getConsultationThreadAction } from '@/app/lib/consultation-action'
import { ConsultationThreadView } from '@/app/ui/consultation-thread'

export default async function ConsultThreadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const consultationId = Number(id)
  if (!Number.isInteger(consultationId)) {
    notFound()
  }

  const thread = await getConsultationThreadAction(consultationId)
  if (!thread) {
    notFound()
  }

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 py-8 sm:px-8">
      <Link href="/consult" className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 내 문의 내역
      </Link>
      <ConsultationThreadView consultationId={consultationId} initialThread={thread} />
    </div>
  )
}
