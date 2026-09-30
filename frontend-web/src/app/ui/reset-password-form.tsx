'use client'

import Link from 'next/link'
import { useActionState } from 'react'
import { confirmPasswordResetAction, type ResetState } from '@/app/lib/password-reset-action'

const initialState: ResetState = { status: 'idle' }
const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

export function ResetPasswordForm({ token }: { token: string }) {
  const [state, formAction, pending] = useActionState(confirmPasswordResetAction.bind(null, token), initialState)

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">새 비밀번호 설정</h1>

      {state.status === 'success' ? (
        <>
          <p className="mt-6 rounded-md border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
            {state.message}
          </p>
          <div className="mt-6 flex gap-2">
            <Link href="/login" className="flex-1 rounded-full bg-orange-500 px-4 py-3 text-center text-sm font-bold text-white hover:bg-orange-600">
              회원 로그인
            </Link>
            <Link href="/lawyer/login" className="flex-1 rounded-full border border-zinc-300 px-4 py-3 text-center text-sm font-semibold hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800">
              변호사 로그인
            </Link>
          </div>
        </>
      ) : (
        <form action={formAction} className="mt-6 flex flex-col gap-4">
          <div>
            <label htmlFor="newPassword" className="mb-1 block text-sm font-medium">
              새 비밀번호 (8자 이상)
            </label>
            <input id="newPassword" name="newPassword" type="password" required minLength={8} autoComplete="new-password" className={inputStyle} />
          </div>
          <div>
            <label htmlFor="confirmPassword" className="mb-1 block text-sm font-medium">
              새 비밀번호 확인
            </label>
            <input id="confirmPassword" name="confirmPassword" type="password" required minLength={8} autoComplete="new-password" className={inputStyle} />
          </div>
          <button
            type="submit"
            disabled={pending}
            className="rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {pending ? '저장 중...' : '새 비밀번호 저장'}
          </button>
          {state.status === 'error' && (
            <p className="text-sm text-red-600 dark:text-red-400">
              {state.message}{' '}
              <Link href="/forgot-password" className="font-semibold underline">
                다시 요청하기
              </Link>
            </p>
          )}
        </form>
      )}
    </div>
  )
}
