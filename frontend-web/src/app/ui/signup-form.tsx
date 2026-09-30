'use client'

import Link from 'next/link'
import { useActionState } from 'react'
import { signupAction, type AuthFormState } from '@/app/lib/auth-action'
import { SocialLoginButtons, type SocialProvider } from '@/app/ui/social-login-buttons'

const initialState: AuthFormState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

export function SignupForm({
  redirectTo,
  oauthError,
  socialProviders,
}: {
  redirectTo: string
  oauthError?: string
  socialProviders: SocialProvider[]
}) {
  const [state, formAction, pending] = useActionState(signupAction.bind(null, redirectTo), initialState)

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">회원가입</h1>
      <p className="mt-2 text-sm text-zinc-500">변호사 무료 상담을 이용하려면 계정이 필요해요.</p>

      {oauthError && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {oauthError}
        </p>
      )}

      {socialProviders.length > 0 && (
        <div className="mt-6">
          <SocialLoginButtons redirectTo={redirectTo} enabled={socialProviders} />
        </div>
      )}

      <div className="my-6 flex items-center gap-3 text-xs text-zinc-400">
        <div className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
        또는 이메일로 가입
        <div className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
      </div>

      <form action={formAction} className="flex flex-col gap-4">
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
            minLength={8}
            autoComplete="new-password"
            className={inputStyle}
          />
          <p className="mt-1 text-xs text-zinc-500">8자 이상으로 입력해주세요.</p>
        </div>

        <button
          type="submit"
          disabled={pending}
          className="mt-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? '가입 중...' : '회원가입'}
        </button>
      </form>

      {state.status === 'error' && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {state.message}
        </p>
      )}

      <p className="mt-6 text-center text-sm text-zinc-500">
        이미 계정이 있으신가요?{' '}
        <Link
          href={`/login?redirect=${encodeURIComponent(redirectTo)}`}
          className="font-semibold text-orange-600 hover:underline dark:text-orange-400"
        >
          로그인
        </Link>
      </p>
    </div>
  )
}
