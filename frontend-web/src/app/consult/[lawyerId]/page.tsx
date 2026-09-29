import { notFound } from 'next/navigation'
import { getLawyerById } from '@/app/lib/lawyers'
import { ConsultChat } from '@/app/ui/consult-chat'

export default async function ConsultPage({ params }: { params: Promise<{ lawyerId: string }> }) {
  const { lawyerId } = await params
  const lawyer = getLawyerById(lawyerId)

  if (!lawyer) {
    notFound()
  }

  return (
    <div className="flex flex-1 flex-col bg-white px-4 py-8 dark:bg-zinc-950 sm:px-8">
      <ConsultChat lawyer={lawyer} />
    </div>
  )
}
