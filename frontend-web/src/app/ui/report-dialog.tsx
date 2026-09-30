'use client'

import { useState } from 'react'
import type { ReportReason, ReportResult } from '@/app/lib/report-types'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const REASONS: { value: ReportReason; label: string; description: string }[] = [
  {
    value: 'ABUSIVE_LANGUAGE',
    label: '욕설·모욕',
    description: '욕설, 비하, 인신공격 등 모욕적인 표현',
  },
  {
    value: 'MONEY_REQUEST',
    label: '금전 요구',
    description: '개인 계좌 입금, 선입금, 수수료 등 돈을 요구하는 행위',
  },
]

/**
 * 상담 대화 신고 팝업. 신고 기준을 욕설·모욕과 금전 요구 두 가지로 한정해서, 이 중 하나를
 * 반드시 골라야만 접수할 수 있다 (백엔드 ReportReason과 같은 기준).
 */
export function ReportDialog({
  counterpartLabel,
  onSubmit,
  onClose,
}: {
  counterpartLabel: string
  onSubmit: (reason: ReportReason, detail: string) => Promise<ReportResult>
  onClose: () => void
}) {
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [detail, setDetail] = useState('')
  const [pending, setPending] = useState(false)
  const [result, setResult] = useState<ReportResult | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!reason) return
    setPending(true)
    setResult(await onSubmit(reason, detail))
    setPending(false)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl dark:bg-zinc-900">
        {result?.ok ? (
          <div className="text-center">
            <h2 className="text-lg font-bold text-zinc-950 dark:text-zinc-50">신고가 접수됐어요</h2>
            <p className="mt-2 text-sm leading-relaxed text-zinc-500">{result.message}</p>
            <button
              type="button"
              onClick={onClose}
              className="mt-5 w-full rounded-full bg-orange-500 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600"
            >
              확인
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <h2 className="text-lg font-bold text-zinc-950 dark:text-zinc-50">{counterpartLabel} 신고하기</h2>
            <p className="mt-1 text-sm text-zinc-500">아래 기준에 해당할 때만 신고할 수 있어요.</p>

            <fieldset className="mt-4 flex flex-col gap-2">
              {REASONS.map((r) => (
                <label
                  key={r.value}
                  className={`flex cursor-pointer gap-3 rounded-xl border p-3 transition-colors ${
                    reason === r.value
                      ? 'border-red-400 bg-red-50 dark:border-red-800 dark:bg-red-950/40'
                      : 'border-zinc-200 hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800'
                  }`}
                >
                  <input
                    type="radio"
                    name="reason"
                    value={r.value}
                    checked={reason === r.value}
                    onChange={() => setReason(r.value)}
                    className="mt-0.5 accent-red-600"
                  />
                  <span>
                    <span className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">{r.label}</span>
                    <span className="block text-xs text-zinc-500">{r.description}</span>
                  </span>
                </label>
              ))}
            </fieldset>

            <textarea
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              maxLength={1000}
              rows={3}
              placeholder="상황을 간단히 적어주세요 (선택)"
              className="mt-3 w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none focus:border-red-400 dark:border-zinc-700"
            />
            <p className="mt-1 text-xs text-zinc-400">관리자가 대화 내용을 직접 확인한 뒤 이용 정지 여부를 결정해요.</p>

            {result && !result.ok && !isLoginRequired(result.message) && (
              <p className="mt-3 text-sm text-red-600 dark:text-red-400">{result.message}</p>
            )}
            <LoginRequiredDialog message={result && !result.ok ? result.message : null} />

            <div className="mt-5 flex gap-2">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={!reason || pending}
                className="flex-1 rounded-full bg-red-600 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {pending ? '접수 중...' : '신고하기'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
