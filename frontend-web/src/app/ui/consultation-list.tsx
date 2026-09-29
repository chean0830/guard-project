import Link from 'next/link'
import type { ConsultationSummary } from '@/app/lib/consultation-action'

export function ConsultationList({ consultations }: { consultations: ConsultationSummary[] }) {
  return (
    <ul className="mt-4 flex flex-col gap-2">
      {consultations.map((c) => (
        <li key={c.id}>
          <Link
            href={`/consult/${c.id}`}
            className="flex items-center justify-between gap-3 rounded-xl border border-zinc-200 p-4 transition-colors hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
          >
            <div className="min-w-0">
              <p className="font-semibold text-zinc-950 dark:text-zinc-50">
                {c.lawyerName}
                {c.lawFirm ? ` · ${c.lawFirm}` : ''}
              </p>
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
