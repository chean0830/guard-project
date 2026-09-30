'use client'

import Link from 'next/link'
import { useActionState, useState } from 'react'
import { requestSignupCodeAction, signupAction, verifySignupCodeAction, type AuthFormState } from '@/app/lib/auth-action'
import { SocialLoginButtons, type SocialProvider } from '@/app/ui/social-login-buttons'

const initialState: AuthFormState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 read-only:bg-zinc-50 read-only:text-zinc-500 dark:border-zinc-700 dark:focus:ring-orange-900/30 dark:read-only:bg-zinc-900'
const smallButton =
  'shrink-0 rounded-xl border border-zinc-300 px-3 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800'

/**
 * 이메일 회원가입. 이메일 인증(6자리 인증번호)을 마쳐야 가입 버튼이 열린다 — 아무 이메일로나 가입해
 * 무료 분석 횟수를 늘리거나 남의 이메일을 선점하지 못하게.
 */
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
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [verificationToken, setVerificationToken] = useState<string | null>(null)
  const [verifyMessage, setVerifyMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSendCode() {
    setBusy(true)
    setVerifyMessage(null)
    const result = await requestSignupCodeAction(email)
    setBusy(false)
    if (!result.ok) {
      setVerifyMessage({ ok: false, text: result.message })
      return
    }
    setCodeSent(true)
    setCode('')
    setVerifyMessage({ ok: true, text: '인증번호를 보냈어요. 메일함(스팸함 포함)을 확인해 10분 안에 입력해주세요.' })
  }

  async function handleVerify() {
    setBusy(true)
    const result = await verifySignupCodeAction(email, code)
    setBusy(false)
    if (!result.ok) {
      setVerifyMessage({ ok: false, text: result.message })
      return
    }
    setVerificationToken(result.token)
    setVerifyMessage({ ok: true, text: '이메일 인증이 완료됐어요.' })
  }

  const verified = verificationToken !== null

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
          <div className="flex gap-2">
            <input
              id="email"
              name="email"
              type="email"
              required
              autoComplete="email"
              value={email}
              readOnly={verified}
              onChange={(e) => {
                setEmail(e.target.value)
                setCodeSent(false)
                setVerifyMessage(null)
              }}
              className={inputStyle}
            />
            {!verified && (
              <button type="button" onClick={handleSendCode} disabled={busy || !email.includes('@')} className={smallButton}>
                {codeSent ? '다시 받기' : '인증번호 받기'}
              </button>
            )}
          </div>
        </div>

        {codeSent && !verified && (
          <div>
            <label htmlFor="code" className="mb-1 block text-sm font-medium">
              인증번호
            </label>
            <div className="flex gap-2">
              <input
                id="code"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="6자리 숫자"
                className={inputStyle}
              />
              <button type="button" onClick={handleVerify} disabled={busy || code.length !== 6} className={smallButton}>
                확인
              </button>
            </div>
          </div>
        )}

        {verifyMessage && (
          <p className={`-mt-2 text-xs ${verifyMessage.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}`}>
            {verified && '✓ '}
            {verifyMessage.text}
          </p>
        )}

        <input type="hidden" name="verificationToken" value={verificationToken ?? ''} />

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
            disabled={!verified}
            className={inputStyle}
          />
          <p className="mt-1 text-xs text-zinc-500">{verified ? '8자 이상으로 입력해주세요.' : '이메일 인증을 마치면 입력할 수 있어요.'}</p>
        </div>

        <button
          type="submit"
          disabled={pending || !verified}
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

      <p className="mt-4 text-center text-xs leading-relaxed text-zinc-400">
        가입하면 <a href="/terms" className="underline">이용약관</a>과 <a href="/privacy" className="underline">개인정보처리방침</a>에 동의하는 것으로 봅니다.
      </p>

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
