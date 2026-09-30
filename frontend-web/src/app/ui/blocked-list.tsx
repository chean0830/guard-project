'use client'

import { useState } from 'react'
import type { BlockedEntry, BlockResult } from '@/app/lib/block-types'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

/** 설정 화면의 "차단 관리" — 내가 차단한 상대 목록과 해제 버튼. */
export function BlockedList({
  initialEntries,
  unblock,
  emptyText,
}: {
  initialEntries: BlockedEntry[]
  unblock: (blockId: number) => Promise<BlockResult>
  emptyText: string
}) {
  const [entries, setEntries] = useState(initialEntries)
  const [error, setError] = useState<string | null>(null)

  async function handleUnblock(entry: BlockedEntry) {
    if (!window.confirm(`${entry.name}의 차단을 해제할까요? 다시 메시지를 주고받을 수 있어요.`)) return
    setError(null)
    const result = await unblock(entry.id)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setEntries((prev) => prev.filter((e) => e.id !== entry.id))
  }

  return (
    <section className="mt-10">
      <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">차단 관리</h2>
      <p className="mt-1 text-xs text-zinc-500">차단한 상대와는 메시지를 주고받을 수 없고, 새 문의에서도 연결되지 않아요.</p>

      {entries.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-zinc-300 p-4 text-center text-sm text-zinc-400 dark:border-zinc-700">
          {emptyText}
        </p>
      ) : (
        <ul className="mt-4 flex flex-col divide-y divide-zinc-200 rounded-xl border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
          {entries.map((entry) => (
            <li key={entry.id} className="flex items-center gap-3 p-4">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-zinc-900 dark:text-zinc-100">{entry.name}</p>
                <p className="text-xs text-zinc-400">
                  {[entry.detail, `${new Date(entry.blockedAt).toLocaleDateString('ko-KR')} 차단`].filter(Boolean).join(' · ')}
                </p>
              </div>
              <button
                type="button"
                onClick={() => handleUnblock(entry)}
                className="shrink-0 rounded-full border border-zinc-300 px-4 py-1.5 text-sm font-semibold text-zinc-600 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
              >
                차단 해제
              </button>
            </li>
          ))}
        </ul>
      )}

      {error && !isLoginRequired(error) && <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
      <LoginRequiredDialog message={error} />
    </section>
  )
}
