'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useState } from 'react'

/** 백엔드는 세션이 없거나 만료되면 항상 "로그인이 필요합니다."(관리자는 "관리자 로그인이 필요합니다.")를 돌려준다. */
export function isLoginRequired(message: string | null | undefined): boolean {
  return Boolean(message && message.includes('로그인이 필요'))
}

function loginPathFor(pathname: string): string {
  const returnTo = encodeURIComponent(pathname)
  if (pathname.startsWith('/lawyer')) return `/lawyer/login?redirect=${returnTo}`
  return `/login?redirect=${returnTo}`
}

/**
 * 요청이 "로그인이 필요합니다"로 실패했을 때 빨간 오류 문구 대신 띄우는 확인 창. "로그인"을 누르면 알맞은
 * 로그인 화면(회원/관리자는 /login, 변호사는 /lawyer/login)으로 가고, 로그인 후 지금 보던 화면으로 돌아온다.
 * message가 바뀌면(새 요청이 또 실패하면) 다시 뜬다.
 */
export function LoginRequiredDialog({ message }: { message: string | null | undefined }) {
  const router = useRouter()
  const pathname = usePathname()
  const [dismissedFor, setDismissedFor] = useState<string | null | undefined>(null)

  if (!isLoginRequired(message) || dismissedFor === message) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4">
      <div role="alertdialog" aria-labelledby="login-required-title" className="w-full max-w-sm rounded-2xl bg-white p-6 text-center shadow-xl dark:bg-zinc-900">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-orange-100 text-2xl dark:bg-orange-500/10">
          🔒
        </div>
        <h2 id="login-required-title" className="text-lg font-bold text-zinc-950 dark:text-zinc-50">
          로그인이 필요합니다
        </h2>
        <p className="mt-2 text-sm text-zinc-500">로그인이 만료됐거나 로그인하지 않은 상태예요. 로그인하시겠습니까?</p>
        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={() => setDismissedFor(message)}
            className="flex-1 rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            취소
          </button>
          <button
            type="button"
            onClick={() => router.push(loginPathFor(pathname))}
            className="flex-1 rounded-full bg-orange-500 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600"
          >
            로그인
          </button>
        </div>
      </div>
    </div>
  )
}
