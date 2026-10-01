'use client'

import { useEffect, useState } from 'react'
import {
  approveLawyerAction,
  downloadLawyerDocumentAction,
  fetchLawyerApplicantsAction,
  rejectLawyerAction,
  revokeLawyerAction,
  type LawyerApplicant,
} from '@/app/lib/admin-lawyer-action'

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

export function AdminLawyerDashboard({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [tab, setTab] = useState<(typeof TABS)[number]['value']>('PENDING')
  const [lawyers, setLawyers] = useState<LawyerApplicant[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [rejectingId, setRejectingId] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  async function loadTab(nextTab: string) {
    setLoading(true)
    setError(null)
    const result = await fetchLawyerApplicantsAction(nextTab)
    setLoading(false)
    if (!result.ok) {
      setError(result.message)
      if (result.message.includes('관리자 로그인')) onUnauthorized()
      return
    }
    setLawyers(result.lawyers)
  }

  useEffect(() => {
    // 최초 진입 시 승인 대기 목록을 불러온다 (탭 전환 시에는 handleTabChange가 다시 불러옴).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadTab('PENDING')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleTabChange(nextTab: (typeof TABS)[number]['value']) {
    setTab(nextTab)
    await loadTab(nextTab)
  }

  async function handleRevoke(lawyer: LawyerApplicant) {
    const confirmed = window.confirm(
      `정말로 ${lawyer.name} 변호사의 승인을 취소하시겠습니까?\n\n` +
        '승인 대기로 돌아가고 바로 로그아웃되며, 다시 승인할 때까지 로그인과 새 상담 배정이 막힙니다.',
    )
    if (!confirmed) return
    const result = await revokeLawyerAction(lawyer.id)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await loadTab(tab)
  }

  async function handleApprove(id: number) {
    const result = await approveLawyerAction(id)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await loadTab(tab)
  }

  async function handleReject(id: number) {
    const result = await rejectLawyerAction(id, rejectReason)
    setRejectingId(null)
    setRejectReason('')
    if (!result.ok) {
      setError(result.message)
      return
    }
    await loadTab(tab)
  }

  async function handleDownload(lawyerId: number, documentId: number) {
    const result = await downloadLawyerDocumentAction(lawyerId, documentId)
    if (!result.ok) {
      setError(result.message)
      return
    }
    downloadBase64File(result.fileName, result.contentType, result.base64)
  }

  return (
    <div>
      <div className="flex gap-2">
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

            {lawyer.status === 'APPROVED' && (
              <div className="mt-4">
                <button
                  onClick={() => handleRevoke(lawyer)}
                  className="rounded-full border border-red-300 px-4 py-1.5 text-sm font-semibold text-red-600 transition-colors hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950"
                >
                  승인 취소
                </button>
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
