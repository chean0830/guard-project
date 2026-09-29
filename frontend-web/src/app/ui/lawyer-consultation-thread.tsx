'use client'

import { useActionState, useEffect, useRef, useState } from 'react'
import {
  getLawyerConsultationThreadAction,
  sendLawyerMessageAction,
  type LawyerConsultationThread,
  type SendMessageState,
} from '@/app/lib/lawyer-consultation-action'

const initialState: SendMessageState = { status: 'idle' }
const POLL_INTERVAL_MS = 5000

export function LawyerConsultationThreadView({
  consultationId,
  initialThread,
}: {
  consultationId: number
  initialThread: LawyerConsultationThread
}) {
  const [thread, setThread] = useState(initialThread)
  const bottomRef = useRef<HTMLDivElement>(null)
  const sendAction = sendLawyerMessageAction.bind(null, consultationId)
  const [state, formAction, pending] = useActionState(sendAction, initialState)
  const formRef = useRef<HTMLFormElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [thread.messages.length])

  useEffect(() => {
    const interval = setInterval(async () => {
      const next = await getLawyerConsultationThreadAction(consultationId)
      if (next) setThread(next)
    }, POLL_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [consultationId])

  useEffect(() => {
    if (state.status === 'idle' && formRef.current) {
      formRef.current.reset()
      getLawyerConsultationThreadAction(consultationId).then((next) => {
        if (next) setThread(next)
      })
    }
  }, [state, consultationId])

  return (
    <div className="flex flex-1 flex-col">
      <div className="border-b border-zinc-200 pb-4 dark:border-zinc-800">
        <p className="font-semibold text-zinc-950 dark:text-zinc-50">{thread.userDisplayName}</p>
        <p className="text-xs text-zinc-500">문의자</p>
      </div>

      <div className="flex flex-1 flex-col gap-3 overflow-y-auto py-4">
        {thread.messages.map((message, index) => (
          <div
            key={index}
            className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap ${
              message.senderType === 'USER'
                ? 'self-start rounded-tl-sm bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100'
                : 'self-end rounded-tr-sm bg-orange-500 text-white'
            }`}
          >
            {message.content}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <form ref={formRef} action={formAction} className="flex gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800">
        <input
          name="content"
          placeholder="답변을 입력하세요"
          className="flex-1 rounded-full border border-zinc-300 bg-transparent px-4 py-2.5 text-sm outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
        />
        <button
          type="submit"
          disabled={pending}
          className="rounded-full bg-orange-500 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          보내기
        </button>
      </form>
      {state.status === 'error' && <p className="mt-2 text-sm text-red-600 dark:text-red-400">{state.message}</p>}
    </div>
  )
}
