'use client'

import Link from 'next/link'
import { useActionState } from 'react'
import { loginAction, type AuthFormState } from '@/app/lib/auth-action'

const initialState: AuthFormState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

export function LoginForm({ redirectTo }: { redirectTo: string }) {
  const [state, formAction, pending] = useActionState(loginAction.bind(null, redirectTo), initialState)

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">로그인</h1>
      <p className="mt-2 text-sm text-zinc-500">변호사 무료 상담을 이용하려면 로그인해주세요.</p>

      <form action={formAction} className="mt-6 flex flex-col gap-4">
        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium">
            이메일
          </label>
          <input id="email" name="email" type="email" required autoComplete="email" className={inputStyle} />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium">
            비밀번호
          </label>
          <input
            id="password"
            name="password"
            type="password"
            required
            autoComplete="current-password"
            className={inputStyle}
          />
        </div>

        <button
          type="submit"
          disabled={pending}
          className="mt-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? '로그인 중...' : '로그인'}
        </button>
      </form>

      {state.status === 'error' && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {state.message}
        </p>
      )}

      <p className="mt-6 text-center text-sm text-zinc-500">
        아직 계정이 없으신가요?{' '}
        <Link
          href={`/signup?redirect=${encodeURIComponent(redirectTo)}`}
          className="font-semibold text-orange-600 hover:underline dark:text-orange-400"
        >
          회원가입
        </Link>
      </p>
    </div>
  )
}
