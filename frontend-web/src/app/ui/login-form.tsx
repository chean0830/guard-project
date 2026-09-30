'use client'

import Link from 'next/link'
import { useActionState } from 'react'
import { loginAction, type AuthFormState } from '@/app/lib/auth-action'
import { SocialLoginButtons, type SocialProvider } from '@/app/ui/social-login-buttons'

const initialState: AuthFormState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

export function LoginForm({
  redirectTo,
  oauthError,
  socialProviders,
}: {
  redirectTo: string
  oauthError?: string
  socialProviders: SocialProvider[]
}) {
  const [state, formAction, pending] = useActionState(loginAction.bind(null, redirectTo), initialState)

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">로그인</h1>
      <p className="mt-2 text-sm text-zinc-500">변호사 무료 상담을 이용하려면 로그인해주세요.</p>

      {oauthError && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {oauthError}
        </p>
      )}

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

      <p className="mt-3 text-right text-sm">
        <Link href="/forgot-password" className="text-zinc-500 hover:text-orange-600 hover:underline">
          비밀번호를 잊으셨나요?
        </Link>
      </p>

      {state.status === 'error' && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {state.message}
        </p>
      )}

      {socialProviders.length > 0 && (
        <>
          <div className="my-6 flex items-center gap-3 text-xs text-zinc-400">
            <div className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
            또는 SNS로 로그인
            <div className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
          </div>
          <SocialLoginButtons redirectTo={redirectTo} enabled={socialProviders} />
        </>
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

      <div className="mt-8 rounded-xl border border-zinc-200 p-5 text-center dark:border-zinc-800">
        <p className="text-sm font-semibold text-zinc-700 dark:text-zinc-200">변호사이신가요?</p>
        <p className="mt-1 text-xs text-zinc-500">승인된 변호사 계정으로 로그인해주세요.</p>
        <Link
          href="/lawyer/login"
          className="mt-4 inline-block w-full rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          변호사 로그인
        </Link>
      </div>
    </div>
  )
}
