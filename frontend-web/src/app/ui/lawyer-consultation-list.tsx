import Link from 'next/link'
import type { LawyerConsultationSummary } from '@/app/lib/lawyer-consultation-action'

export function LawyerConsultationList({ consultations }: { consultations: LawyerConsultationSummary[] }) {
  if (consultations.length === 0) {
    return <p className="mt-6 text-sm text-zinc-500">아직 들어온 문의가 없어요.</p>
  }

  return (
    <ul className="mt-4 flex flex-col gap-2">
      {consultations.map((c) => (
        <li key={c.id}>
          <Link
            href={`/lawyer/consultations/${c.id}`}
            className="flex items-center justify-between gap-3 rounded-xl border border-zinc-200 p-4 transition-colors hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
          >
            <div className="min-w-0">
              <p className="font-semibold text-zinc-950 dark:text-zinc-50">{c.userDisplayName}</p>
              <p className="mt-0.5 truncate text-sm text-zinc-500">{c.lastMessagePreview}</p>
            </div>
            {c.unreadCount > 0 && (
              <span className="shrink-0 rounded-full bg-orange-500 px-2 py-0.5 text-xs font-bold text-white">
                {c.unreadCount}
              </span>
            )}
          </Link>
        </li>
      ))}
    </ul>
  )
}
