'use client'

import { useState } from 'react'
import {
  approveLawyerAction,
  downloadLawyerDocumentAction,
  fetchLawyerApplicantsAction,
  rejectLawyerAction,
  type LawyerApplicant,
} from '@/app/lib/admin-lawyer-action'

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

const TABS = [
  { value: 'PENDING', label: '승인 대기' },
  { value: 'APPROVED', label: '승인됨' },
  { value: 'REJECTED', label: '거절됨' },
] as const

const STATUS_BADGE: Record<LawyerApplicant['status'], string> = {
  PENDING: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400',
  APPROVED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400',
  REJECTED: 'bg-red-100 text-red-700 dark:bg-red-500/10 dark:text-red-400',
}

function downloadBase64File(fileName: string, contentType: string, base64: string) {
  const byteChars = atob(base64)
  const byteNumbers = new Array(byteChars.length)
  for (let i = 0; i < byteChars.length; i++) {
    byteNumbers[i] = byteChars.charCodeAt(i)
  }
  const blob = new Blob([new Uint8Array(byteNumbers)], { type: contentType })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

export function AdminLawyerDashboard() {
  const [secret, setSecret] = useState('')
  const [secretInput, setSecretInput] = useState('')
  const [tab, setTab] = useState<(typeof TABS)[number]['value']>('PENDING')
  const [lawyers, setLawyers] = useState<LawyerApplicant[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [rejectingId, setRejectingId] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  async function loadTab(nextSecret: string, nextTab: string) {
    setLoading(true)
    setError(null)
    const result = await fetchLawyerApplicantsAction(nextSecret, nextTab)
    setLoading(false)
    if (!result.ok) {
      setError(result.message)
      if (result.message.includes('비밀키')) setSecret('')
      return
    }
    setLawyers(result.lawyers)
  }

  async function handleUnlock(e: React.FormEvent) {
    e.preventDefault()
    setSecret(secretInput)
    await loadTab(secretInput, tab)
  }

  async function handleTabChange(nextTab: (typeof TABS)[number]['value']) {
    setTab(nextTab)
    await loadTab(secret, nextTab)
  }

  async function handleApprove(id: number) {
    const result = await approveLawyerAction(secret, id)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await loadTab(secret, tab)
  }

  async function handleReject(id: number) {
    const result = await rejectLawyerAction(secret, id, rejectReason)
    setRejectingId(null)
    setRejectReason('')
    if (!result.ok) {
      setError(result.message)
      return
    }
    await loadTab(secret, tab)
  }

  async function handleDownload(lawyerId: number, documentId: number) {
    const result = await downloadLawyerDocumentAction(secret, lawyerId, documentId)
    if (!result.ok) {
      setError(result.message)
      return
    }
    downloadBase64File(result.fileName, result.contentType, result.base64)
  }

  if (!secret) {
    return (
      <div className="mx-auto w-full max-w-sm">
        <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">관리자 로그인</h1>
        <p className="mt-2 text-sm text-zinc-500">변호사 가입 신청을 검토하려면 관리자 비밀키를 입력해주세요.</p>

        <form onSubmit={handleUnlock} className="mt-6 flex flex-col gap-4">
          <input
            type="password"
            value={secretInput}
            onChange={(e) => setSecretInput(e.target.value)}
            placeholder="ADMIN_SECRET"
            required
            className={inputStyle}
          />
          <button
            type="submit"
            disabled={loading}
            className="rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? '확인 중...' : '입장'}
          </button>
        </form>

        {error && (
          <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-3xl">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">변호사 가입 신청 검토</h1>

      <div className="mt-6 flex gap-2">
        {TABS.map((t) => (
          <button
            key={t.value}
            onClick={() => handleTabChange(t.value)}
            className={`rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
              tab === t.value
                ? 'bg-orange-500 text-white'
                : 'border border-zinc-300 text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      {loading && <p className="mt-6 text-sm text-zinc-500">불러오는 중...</p>}

      {!loading && lawyers.length === 0 && <p className="mt-6 text-sm text-zinc-500">해당 상태의 신청이 없습니다.</p>}

      <ul className="mt-6 flex flex-col gap-4">
        {lawyers.map((lawyer) => (
          <li
            key={lawyer.id}
            className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="font-semibold">
                  {lawyer.name}{' '}
                  <span className={`ml-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ${STATUS_BADGE[lawyer.status]}`}>
                    {lawyer.status}
                  </span>
                </p>
                <p className="text-sm text-zinc-500">{lawyer.email}</p>
              </div>
              <p className="text-xs text-zinc-400">{new Date(lawyer.createdAt).toLocaleString('ko-KR')}</p>
            </div>

            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <dt className="text-zinc-500">소속</dt>
              <dd>{lawyer.lawFirm ?? '-'}</dd>
              <dt className="text-zinc-500">등록번호</dt>
              <dd>{lawyer.barNumber}</dd>
              {lawyer.status === 'REJECTED' && (
                <>
                  <dt className="text-zinc-500">거절 사유</dt>
                  <dd>{lawyer.rejectionReason ?? '-'}</dd>
                </>
              )}
            </dl>

            {lawyer.documents.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-2">
                {lawyer.documents.map((doc) => (
                  <button
                    key={doc.id}
                    onClick={() => handleDownload(lawyer.id, doc.id)}
                    className="rounded-full border border-zinc-300 px-3 py-1 text-xs font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
                  >
                    {doc.fileName}
                  </button>
                ))}
              </div>
            )}

            {lawyer.status === 'PENDING' && (
              <div className="mt-4 flex flex-wrap items-center gap-2">
                <button
                  onClick={() => handleApprove(lawyer.id)}
                  className="rounded-full bg-emerald-600 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
                >
                  승인
                </button>
                {rejectingId === lawyer.id ? (
                  <>
                    <input
                      type="text"
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="거절 사유 (선택)"
                      className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm dark:border-zinc-700"
                    />
                    <button
                      onClick={() => handleReject(lawyer.id)}
                      className="rounded-full bg-red-600 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-red-700"
                    >
                      거절 확정
                    </button>
                    <button
                      onClick={() => setRejectingId(null)}
                      className="text-sm text-zinc-500 hover:underline"
                    >
                      취소
                    </button>
                  </>
                ) : (
                  <button
                    onClick={() => setRejectingId(lawyer.id)}
                    className="rounded-full border border-red-300 px-4 py-1.5 text-sm font-semibold text-red-600 transition-colors hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950"
                  >
                    거절
                  </button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
