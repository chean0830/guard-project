'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import type { Lawyer } from '@/app/lib/lawyers'

type Message = {
  id: number
  sender: 'user' | 'lawyer'
  text: string
}

const SCRIPTED_REPLIES = [
  '말씀해주셔서 감사해요. 상황을 정확히 보려면 등기부등본과 계약서를 함께 확인해야 해요.',
  '다만 이 채팅은 포트폴리오 데모라서 실제 상담으로 이어지지는 않아요. 실제 도움이 필요하시면 아래 대한법률구조공단(국번없이 132)으로 연락해보세요.',
  '이 화면은 데모 채팅이라 여기서 더 답을 드리긴 어려워요. 실제 상담은 대한법률구조공단(132)이나 변호사 단체를 통해 받아보실 수 있어요.',
]

export function ConsultChat({ lawyer }: { lawyer: Lawyer }) {
  const [messages, setMessages] = useState<Message[]>([{ id: 0, sender: 'lawyer', text: lawyer.intro }])
  const [draft, setDraft] = useState('')
  const [isTyping, setIsTyping] = useState(false)
  const nextId = useRef(1)
  const bottomRef = useRef<HTMLDivElement>(null)
  const userMessageCount = useRef(0)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, isTyping])

  function handleSend(e: React.FormEvent) {
    e.preventDefault()
    const text = draft.trim()
    if (!text) return

    const userMessage: Message = { id: nextId.current++, sender: 'user', text }
    setMessages((prev) => [...prev, userMessage])
    setDraft('')
    setIsTyping(true)

    const replyIndex = Math.min(userMessageCount.current, SCRIPTED_REPLIES.length - 1)
    userMessageCount.current += 1

    setTimeout(() => {
      setMessages((prev) => [...prev, { id: nextId.current++, sender: 'lawyer', text: SCRIPTED_REPLIES[replyIndex] }])
      setIsTyping(false)
    }, 900)
  }

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col">
      <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-center text-xs text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100">
        이 상담은 포트폴리오 데모이며 실제 변호사 상담이 아닙니다. 실제 법률 상담은{' '}
        <strong>대한법률구조공단(국번없이 132)</strong> 등 공식 기관을 이용해주세요.
      </div>

      <div className="mt-4 flex items-center gap-3 border-b border-zinc-200 pb-4 dark:border-zinc-800">
        <div
          className={`flex h-11 w-11 items-center justify-center rounded-full text-sm font-bold text-white ${lawyer.avatarColor}`}
        >
          {lawyer.name.slice(0, 1)}
        </div>
        <div>
          <p className="font-semibold text-zinc-950 dark:text-zinc-50">{lawyer.name}</p>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            {lawyer.firm} · {lawyer.specialties.join(', ')}
          </p>
          <p className="mt-0.5 text-xs text-emerald-600 dark:text-emerald-400">✅ {lawyer.experience}</p>
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-3 overflow-y-auto py-4">
        {messages.map((message) => (
          <div
            key={message.id}
            className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed ${
              message.sender === 'lawyer'
                ? 'self-start rounded-tl-sm bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100'
                : 'self-end rounded-tr-sm bg-orange-500 text-white'
            }`}
          >
            {message.text}
          </div>
        ))}
        {isTyping && (
          <div className="self-start rounded-2xl rounded-tl-sm bg-zinc-100 px-4 py-2.5 text-sm text-zinc-400 dark:bg-zinc-800">
            {lawyer.name}님이 입력 중...
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={handleSend} className="flex gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800">
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="메시지를 입력하세요"
          className="flex-1 rounded-full border border-zinc-300 bg-transparent px-4 py-2.5 text-sm outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
        />
        <button
          type="submit"
          className="rounded-full bg-orange-500 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600"
        >
          보내기
        </button>
      </form>

      <Link href="/" className="mt-4 text-center text-xs text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 홈으로 돌아가기
      </Link>
    </div>
  )
}
