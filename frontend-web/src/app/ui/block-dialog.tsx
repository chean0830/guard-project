'use client'

import { useState } from 'react'
import type { BlockResult } from '@/app/lib/block-types'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

/** 대화방에서 상대방을 차단하기 전에 효과를 알려주고 한 번 더 확인받는 창. */
export function BlockDialog({
  counterpartLabel,
  onConfirm,
  onDone,
  onClose,
}: {
  counterpartLabel: string
  onConfirm: () => Promise<BlockResult>
  onDone: () => void
  onClose: () => void
}) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setPending(true)
    setError(null)
    const result = await onConfirm()
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    onDone()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div role="dialog" aria-labelledby="block-dialog-title" className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl dark:bg-zinc-900">
        <h2 id="block-dialog-title" className="text-lg font-bold text-zinc-950 dark:text-zinc-50">
          이 {counterpartLabel}를 차단할까요?
        </h2>
        <ul className="mt-3 flex flex-col gap-1.5 text-sm leading-relaxed text-zinc-600 dark:text-zinc-300">
          <li>· 서로 더 이상 메시지를 주고받을 수 없어요.</li>
          <li>· 새 문의에서도 서로 연결되지 않아요.</li>
          <li>· 설정의 &lsquo;차단 관리&rsquo;에서 언제든 해제할 수 있어요.</li>
        </ul>
        <p className="mt-3 text-xs text-zinc-400">욕설이나 금전 요구가 있었다면 🚨 신고도 함께 해주세요.</p>

        {error && !isLoginRequired(error) && <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
        <LoginRequiredDialog message={error} />

        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={pending}
            className="flex-1 rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
          >
            {pending ? '차단 중...' : '차단하기'}
          </button>
        </div>
      </div>
    </div>
  )
}
