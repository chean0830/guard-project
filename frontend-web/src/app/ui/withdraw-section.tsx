'use client'

import { useState } from 'react'
import { withdrawAction } from '@/app/lib/profile-action'

/** 설정 화면 맨 아래의 회원 탈퇴. 이메일 가입은 비밀번호, 소셜 전용 계정은 '탈퇴' 입력으로 확인한다. */
export function WithdrawSection({ hasPassword }: { hasPassword: boolean }) {
  const [open, setOpen] = useState(false)
  const [value, setValue] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleWithdraw() {
    if (!window.confirm('정말 탈퇴할까요? 상담 대화와 계정 정보가 삭제되고 되돌릴 수 없어요.')) return
    setPending(true)
    setError(null)
    const result = await withdrawAction(hasPassword ? { password: value } : { confirmText: value })
    setPending(false)
    if (result && !result.ok) setError(result.message)
  }

  return (
    <section className="mt-12 border-t border-zinc-200 pt-8 dark:border-zinc-800">
      <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">회원 탈퇴</h2>
      <ul className="mt-2 flex flex-col gap-1 text-xs leading-relaxed text-zinc-500">
        <li>· 계정 정보와 상담 대화, 차단 목록이 삭제되며 되돌릴 수 없어요.</li>
        <li>· 결제 기록은 전자상거래법에 따라 5년 동안 보관돼요.</li>
        <li>· 쓰지 않은 이용권이나 검토 중인 환불이 있으면 먼저 정리해야 탈퇴할 수 있어요.</li>
      </ul>

      {open ? (
        <div className="mt-4 flex flex-col gap-2">
          <input
            type={hasPassword ? 'password' : 'text'}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={hasPassword ? '현재 비밀번호' : "확인을 위해 '탈퇴'를 입력하세요"}
            autoComplete={hasPassword ? 'current-password' : 'off'}
            className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none focus:border-red-400 dark:border-zinc-700"
          />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleWithdraw}
              disabled={pending || !value}
              className="rounded-full bg-red-600 px-4 py-2 text-sm font-bold text-white transition-colors hover:bg-red-700 disabled:opacity-50"
            >
              {pending ? '처리 중...' : '탈퇴하기'}
            </button>
            <button type="button" onClick={() => setOpen(false)} className="px-3 text-sm text-zinc-500 hover:underline">
              취소
            </button>
          </div>
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        </div>
      ) : (
        <button type="button" onClick={() => setOpen(true)} className="mt-4 text-sm text-zinc-400 underline hover:text-red-600">
          회원 탈퇴
        </button>
      )}
    </section>
  )
}
