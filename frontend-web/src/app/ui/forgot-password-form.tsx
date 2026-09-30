'use client'

import Link from 'next/link'
import { useActionState } from 'react'
import { requestPasswordResetAction, type ResetState } from '@/app/lib/password-reset-action'

const initialState: ResetState = { status: 'idle' }
const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

export function ForgotPasswordForm({ lawyer }: { lawyer: boolean }) {
  const [state, formAction, pending] = useActionState(requestPasswordResetAction, initialState)

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">비밀번호 찾기</h1>
      <p className="mt-2 text-sm leading-relaxed text-zinc-500">
        가입한 이메일로 비밀번호 재설정 링크를 보내드려요. 비밀번호를 5회 틀려 로그인이 잠긴 경우에도 여기서 새 비밀번호를
        설정하면 잠금이 풀려요.
      </p>

      {state.status === 'success' ? (
        <p className="mt-6 rounded-md border border-emerald-300 bg-emerald-50 p-4 text-sm leading-relaxed text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
          {state.message}
        </p>
      ) : (
        <form action={formAction} className="mt-6 flex flex-col gap-4">
          <fieldset className="flex gap-2">
            {[
              { value: 'USER', label: '회원' },
              { value: 'LAWYER', label: '변호사' },
            ].map((option) => (
              <label
                key={option.value}
                className="flex flex-1 cursor-pointer items-center justify-center gap-2 rounded-xl border border-zinc-300 px-3 py-2 text-sm has-[:checked]:border-orange-400 has-[:checked]:bg-orange-50 dark:border-zinc-700 dark:has-[:checked]:bg-orange-950/30"
              >
                <input
                  type="radio"
                  name="accountType"
                  value={option.value}
                  defaultChecked={(option.value === 'LAWYER') === lawyer}
                  className="accent-orange-500"
                />
                {option.label}
              </label>
            ))}
          </fieldset>
          <div>
            <label htmlFor="email" className="mb-1 block text-sm font-medium">
              가입한 이메일
            </label>
            <input id="email" name="email" type="email" required autoComplete="email" className={inputStyle} />
          </div>
          <button
            type="submit"
            disabled={pending}
            className="rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {pending ? '보내는 중...' : '재설정 링크 받기'}
          </button>
          {state.status === 'error' && <p className="text-sm text-red-600 dark:text-red-400">{state.message}</p>}
        </form>
      )}

      <p className="mt-6 text-center text-sm text-zinc-500">
        <Link href={lawyer ? '/lawyer/login' : '/login'} className="font-semibold text-orange-600 hover:underline dark:text-orange-400">
          로그인으로 돌아가기
        </Link>
      </p>
    </div>
  )
}
