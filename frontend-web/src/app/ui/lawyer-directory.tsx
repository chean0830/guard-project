'use client'

import { useActionState, useState } from 'react'
import { startDirectConsultationAction, type DirectoryLawyer, type StartDirectState } from '@/app/lib/payment-action'
import { ConsultationSubmittedModal } from '@/app/ui/consultation-submitted-modal'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const initialState: StartDirectState = { status: 'idle' }

function DirectConsultationForm({ lawyer, onCancel }: { lawyer: DirectoryLawyer; onCancel: () => void }) {
  const action = startDirectConsultationAction.bind(null, lawyer.id)
  const [state, formAction, pending] = useActionState(action, initialState)

  return (
    <>
      <form action={formAction} className="mt-4 flex flex-col gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
        <textarea
          name="message"
          required
          rows={4}
          autoFocus
          placeholder={`${lawyer.name} 변호사님께 보낼 첫 문의를 적어주세요.`}
          className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
        />
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={pending}
            className="rounded-full bg-orange-500 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {pending ? '등록 중...' : '이용권 1장 쓰고 문의 보내기'}
          </button>
          <button type="button" onClick={onCancel} className="px-3 text-sm text-zinc-500 hover:underline">
            취소
          </button>
        </div>
        <LoginRequiredDialog message={state.status === 'error' ? state.message : null} />
        {state.status === 'error' && !isLoginRequired(state.message) && (
          <p className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
            {state.message}
          </p>
        )}
      </form>
      {state.status === 'success' && <ConsultationSubmittedModal consultationId={state.consultationId} />}
    </>
  )
}

function hasStrengths(lawyer: DirectoryLawyer) {
  return Boolean(lawyer.headline || lawyer.feeInfo || lawyer.achievements || lawyer.specialties || lawyer.introduction)
}

/** 이름 바로 아래에 보여주는 강점 — 회원이 변호사를 비교해서 고를 수 있게 한다. */
function LawyerStrengths({ lawyer }: { lawyer: DirectoryLawyer }) {
  if (!hasStrengths(lawyer)) {
    return <p className="mt-3 text-sm text-zinc-400">아직 강점을 등록하지 않은 변호사예요.</p>
  }
  const achievements = (lawyer.achievements ?? '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  const specialties = (lawyer.specialties ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)

  return (
    <div className="mt-3 flex flex-col gap-3">
      {lawyer.headline && <p className="font-semibold text-orange-600 dark:text-orange-400">{lawyer.headline}</p>}

      {specialties.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {specialties.map((s) => (
            <span key={s} className="rounded-full bg-zinc-100 px-2.5 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
              {s}
            </span>
          ))}
        </div>
      )}

      {lawyer.feeInfo && (
        <div className="rounded-lg bg-zinc-50 px-3 py-2 dark:bg-zinc-900">
          <p className="text-xs font-semibold text-zinc-500">수임료</p>
          <p className="text-sm text-zinc-800 dark:text-zinc-200">{lawyer.feeInfo}</p>
        </div>
      )}

      {achievements.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-zinc-500">주요 실적</p>
          <ul className="mt-1 flex flex-col gap-0.5">
            {achievements.map((a) => (
              <li key={a} className="text-sm text-zinc-800 dark:text-zinc-200">
                ✓ {a}
              </li>
            ))}
          </ul>
        </div>
      )}

      {lawyer.introduction && (
        <p className="text-sm leading-relaxed whitespace-pre-wrap text-zinc-600 dark:text-zinc-300">{lawyer.introduction}</p>
      )}
    </div>
  )
}

/** 이용권이 있는 회원에게 보여주는 입점 변호사 목록. 한 명을 골라 첫 문의를 보내면 이용권 1장이 차감된다. */
export function LawyerDirectory({ lawyers, credits }: { lawyers: DirectoryLawyer[]; credits: number }) {
  const [selectedId, setSelectedId] = useState<number | null>(null)

  return (
    <div>
      <p className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200">
        사용 가능한 이용권 <strong>{credits}장</strong> · 상담할 변호사를 골라주세요.
      </p>
      <p className="mt-2 text-xs text-zinc-400">
        강점·수임료·실적은 변호사가 직접 작성한 정보예요. 자격은 관리자가 서류로 확인했어요.
      </p>

      {lawyers.length === 0 && (
        <p className="mt-6 text-sm text-zinc-500">지금 상담 가능한 변호사가 없어요. 이용권은 그대로 남아 있으니 잠시 후 다시 확인해주세요.</p>
      )}

      <ul className="mt-6 flex flex-col gap-4">
        {lawyers.map((lawyer) => (
          <li key={lawyer.id} className="rounded-2xl border border-zinc-200 p-5 dark:border-zinc-800">
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-orange-500 text-lg font-bold text-white">
                {lawyer.name.slice(0, 1)}
              </div>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-zinc-950 dark:text-zinc-50">
                  {lawyer.name} <span className="text-sm font-normal text-zinc-500">변호사</span>
                </p>
                <p className="text-sm text-zinc-500">
                  {[lawyer.lawFirm, lawyer.careerYears != null ? `경력 ${lawyer.careerYears}년` : null]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
                <LawyerStrengths lawyer={lawyer} />
              </div>
              {selectedId !== lawyer.id && (
                <button
                  type="button"
                  onClick={() => setSelectedId(lawyer.id)}
                  className="shrink-0 rounded-full bg-orange-500 px-4 py-2 text-sm font-bold text-white transition-colors hover:bg-orange-600"
                >
                  상담하기
                </button>
              )}
            </div>
            {selectedId === lawyer.id && <DirectConsultationForm lawyer={lawyer} onCancel={() => setSelectedId(null)} />}
          </li>
        ))}
      </ul>
    </div>
  )
}
