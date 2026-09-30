'use client'

import { useActionState, useCallback, useEffect, useRef, useState } from 'react'
import {
  getConsultationThreadAction,
  getConsultationSocketTicketAction,
  sendConsultationMessageAction,
  type ConsultationThread,
  type SendMessageState,
  blockConsultationCounterpartAction,
  reportConsultationAction,
} from '@/app/lib/consultation-action'
import Link from 'next/link'
import { BlockDialog } from '@/app/ui/block-dialog'
import { ChatPolicyNotice } from '@/app/ui/chat-policy-notice'
import { ReportDialog } from '@/app/ui/report-dialog'
import { useConsultationSocket } from '@/app/ui/use-consultation-socket'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const initialState: SendMessageState = { status: 'idle' }
// 새 메시지는 WebSocket으로 즉시 받는다. 이 주기 조회는 연결이 끊긴 사이를 메우는 안전망일 뿐이다.
const FALLBACK_POLL_INTERVAL_MS = 30_000

export function ConsultationThreadView({
  consultationId,
  initialThread,
}: {
  consultationId: number
  initialThread: ConsultationThread
}) {
  const [thread, setThread] = useState(initialThread)
  const bottomRef = useRef<HTMLDivElement>(null)
  const sendAction = sendConsultationMessageAction.bind(null, consultationId)
  const [state, formAction, pending] = useActionState(sendAction, initialState)
  const formRef = useRef<HTMLFormElement>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [blockOpen, setBlockOpen] = useState(false)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [thread.messages.length])

  const refreshThread = useCallback(async () => {
    const next = await getConsultationThreadAction(consultationId)
    if (next) setThread(next)
  }, [consultationId])

  useConsultationSocket(consultationId, getConsultationSocketTicketAction, refreshThread)

  useEffect(() => {
    const interval = setInterval(refreshThread, FALLBACK_POLL_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [refreshThread])

  useEffect(() => {
    if (state.status === 'idle' && formRef.current) {
      formRef.current.reset()
      getConsultationThreadAction(consultationId).then((next) => {
        if (next) setThread(next)
      })
    }
  }, [state, consultationId])

  return (
    <div className="flex flex-1 flex-col">
      <div className="flex items-center gap-3 border-b border-zinc-200 pb-4 dark:border-zinc-800">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-orange-500 text-sm font-bold text-white">
          {thread.lawyerName.slice(0, 1)}
        </div>
        <div>
          <p className="font-semibold text-zinc-950 dark:text-zinc-50">{thread.lawyerName}</p>
          {thread.lawFirm && <p className="text-xs text-zinc-500 dark:text-zinc-400">{thread.lawFirm}</p>}
        </div>
        <button
          type="button"
          onClick={() => setReportOpen(true)}
          aria-label="신고"
          title="신고하기"
          className="ml-auto flex h-9 w-9 items-center justify-center rounded-full text-lg transition-colors hover:bg-red-50 dark:hover:bg-red-950/40"
        >
          🚨
        </button>
        {thread.blockState !== 'BLOCKED_BY_ME' && (
          <button
            type="button"
            onClick={() => setBlockOpen(true)}
            aria-label="차단"
            title="차단하기"
            className="flex h-9 w-9 items-center justify-center rounded-full text-lg transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800"
          >
            🚫
          </button>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 overflow-y-auto py-4">
        {thread.messages.map((message, index) => (
          <div
            key={index}
            className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap ${
              message.senderType === 'LAWYER'
                ? 'self-start rounded-tl-sm bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100'
                : 'self-end rounded-tr-sm bg-orange-500 text-white'
            }`}
          >
            {message.content}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {thread.counterpartBlocked ? (
        <p className="border-t border-zinc-200 pt-4 text-center text-sm text-zinc-500 dark:border-zinc-800">
          상대방이 이용 정지되어 더 이상 대화할 수 없어요.
        </p>
      ) : thread.blockState === 'BLOCKED_BY_ME' ? (
        <p className="border-t border-zinc-200 pt-4 text-center text-sm text-zinc-500 dark:border-zinc-800">
          차단한 상대예요. 메시지를 주고받을 수 없어요.{' '}
          <Link href="/settings" className="font-semibold text-orange-600 hover:underline dark:text-orange-400">
            차단 관리
          </Link>
        </p>
      ) : thread.blockState === 'BLOCKED_ME' ? (
        <p className="border-t border-zinc-200 pt-4 text-center text-sm text-zinc-500 dark:border-zinc-800">
          상대방에게 메시지를 보낼 수 없어요.
        </p>
      ) : (
        <form ref={formRef} action={formAction} className="flex gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800">
          <input
            name="content"
            placeholder="메시지를 입력하세요"
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
      )}
      {state.status === 'error' && !isLoginRequired(state.message) && (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400">{state.message}</p>
      )}
      <LoginRequiredDialog message={state.status === 'error' ? state.message : null} />
      <ChatPolicyNotice role="user" />
      {blockOpen && (
        <BlockDialog
          counterpartLabel="변호사"
          onConfirm={() => blockConsultationCounterpartAction(consultationId)}
          onDone={() => {
            setBlockOpen(false)
            refreshThread()
          }}
          onClose={() => setBlockOpen(false)}
        />
      )}
      {reportOpen && (
        <ReportDialog
          counterpartLabel="변호사"
          onSubmit={(reason, detail) => reportConsultationAction(consultationId, reason, detail)}
          onClose={() => setReportOpen(false)}
        />
      )}
    </div>
  )
}
