'use client'

import { useEffect, useState } from 'react'
import {
  fetchLawyerMessagesAction,
  sendLawyerMessageAction,
  type AdminLawyerMessage,
} from '@/app/lib/admin-moderation-action'

/** 회원 관리 > 변호사 행 아래에 펼치는 1:1 메시지 창: 보낸 기록(읽음 여부)과 입력창. 변호사는 답장할 수 없다. */
export function AdminLawyerMessages({ lawyerId, lawyerName }: { lawyerId: number; lawyerName: string }) {
  const [messages, setMessages] = useState<AdminLawyerMessage[] | null>(null)
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchLawyerMessagesAction(lawyerId).then((result) => {
      if (result.ok) setMessages(result.data)
      else setError(result.message)
    })
  }, [lawyerId])

  async function handleSend() {
    const text = content.trim()
    if (!text) return
    setSending(true)
    const result = await sendLawyerMessageAction(lawyerId, text)
    setSending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setError(null)
    setContent('')
    setMessages((prev) => [result.data, ...(prev ?? [])])
  }

  return (
    <div className="w-full rounded-lg bg-zinc-50 p-4 dark:bg-zinc-900">
      <p className="text-xs text-zinc-500">
        {lawyerName} 변호사의 관리자 메시지 알림함으로 전달되고 앱 푸시도 갑니다. 변호사는 답장할 수 없습니다.
      </p>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={2000}
          rows={2}
          placeholder="변호사에게 보낼 내용"
          className="min-w-0 flex-1 rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm outline-none focus:border-orange-400 dark:border-zinc-700 dark:bg-zinc-950"
        />
        <button
          onClick={handleSend}
          disabled={sending || !content.trim()}
          className="self-end rounded-full bg-orange-500 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-orange-600 disabled:opacity-50"
        >
          {sending ? '보내는 중...' : '보내기'}
        </button>
      </div>
      {error && <p className="mt-2 text-sm text-red-600 dark:text-red-400">{error}</p>}
      <ul className="mt-4 flex flex-col gap-2">
        {messages === null && !error && <li className="text-sm text-zinc-500">불러오는 중...</li>}
        {messages?.length === 0 && <li className="text-sm text-zinc-500">아직 보낸 메시지가 없습니다.</li>}
        {messages?.map((m) => (
          <li key={m.id} className="rounded-lg border border-zinc-200 bg-white p-3 text-sm dark:border-zinc-800 dark:bg-zinc-950">
            <div className="mb-1 flex items-center justify-between gap-2 text-xs text-zinc-400">
              <span>{new Date(m.createdAt).toLocaleString('ko-KR')}</span>
              {m.readAt ? (
                <span className="text-emerald-600 dark:text-emerald-400">읽음 {new Date(m.readAt).toLocaleString('ko-KR')}</span>
              ) : (
                <span className="font-semibold text-amber-600 dark:text-amber-400">안 읽음</span>
              )}
            </div>
            <p className="whitespace-pre-wrap">{m.content}</p>
          </li>
        ))}
      </ul>
    </div>
  )
}
