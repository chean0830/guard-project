'use client'

import { useActionState } from 'react'
import { startConsultationAction, type StartConsultationState } from '@/app/lib/consultation-action'

const initialState: StartConsultationState = { status: 'idle' }

export function StartConsultationForm() {
  const [state, formAction, pending] = useActionState(startConsultationAction, initialState)

  return (
    <form action={formAction} className="mt-4 flex flex-col gap-3">
      <textarea
        name="message"
        required
        rows={4}
        placeholder="예: 등기부에 근저당이 있는데 계약해도 괜찮을까요? 상황을 자세히 적어주시면 더 정확한 답변을 받을 수 있어요."
        className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
      />
      <button
        type="submit"
        disabled={pending}
        className="self-start rounded-full bg-orange-500 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {pending ? '등록 중...' : '문의 보내기'}
      </button>
      {state.status === 'error' && (
        <p className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {state.message}
        </p>
      )}
    </form>
  )
}
