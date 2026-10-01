import Link from 'next/link'
import { redirect } from 'next/navigation'
import {
  listLawyerAdminMessagesAction,
  markLawyerAdminMessagesReadAction,
} from '@/app/lib/lawyer-admin-message-action'

/** 변호사의 관리자 메시지 알림함 (읽기 전용). 목록을 먼저 받아 안 읽음 표시를 보여주고, 그다음 모두 읽음 처리한다. */
export default async function LawyerAdminMessagesPage() {
  const messages = await listLawyerAdminMessagesAction()
  if (!messages) {
    redirect('/lawyer/login')
  }
  if (messages.some((m) => m.readAt === null)) {
    await markLawyerAdminMessagesReadAction()
  }

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 py-12 sm:px-8">
      <Link href="/lawyer" className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 마이페이지
      </Link>
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">관리자 메시지</h1>
      <p className="mt-2 text-sm text-zinc-500">Project Guard 운영팀이 보낸 안내입니다. 문의는 회신 메일로 보내주세요.</p>

      {messages.length === 0 ? (
        <p className="mt-10 text-center text-sm text-zinc-500">받은 메시지가 없습니다.</p>
      ) : (
        <ul className="mt-8 flex flex-col gap-3">
          {messages.map((m) => (
            <li
              key={m.id}
              className={`rounded-xl border p-4 ${
                m.readAt === null ? 'border-orange-400 dark:border-orange-600' : 'border-zinc-200 dark:border-zinc-800'
              }`}
            >
              <p className="mb-1 flex items-center gap-1.5 text-xs text-zinc-400">
                {m.readAt === null && <span className="h-1.5 w-1.5 rounded-full bg-orange-500" />}
                {new Date(m.createdAt).toLocaleString('ko-KR')}
              </p>
              <p className="whitespace-pre-wrap text-sm text-zinc-900 dark:text-zinc-100">{m.content}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
