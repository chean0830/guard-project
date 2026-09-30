'use client'

import { useEffect, useState } from 'react'
import {
  actionReportAction,
  dismissReportAction,
  fetchReportMessagesAction,
  fetchReportsAction,
  type AdminReport,
  type AdminReportMessage,
  type ReportStatus,
} from '@/app/lib/admin-moderation-action'

const TABS: { value: ReportStatus; label: string }[] = [
  { value: 'PENDING', label: '처리 대기' },
  { value: 'ACTIONED', label: '정지 처리됨' },
  { value: 'DISMISSED', label: '기각됨' },
]

const PARTY_LABEL = { USER: '회원', LAWYER: '변호사' } as const

/**
 * 신고 처리 화면. 신고 기준(욕설·모욕 / 금전 요구) 충족 여부는 관리자가 대화 원문을 직접
 * 보고 판단한다 — 신고된 쪽 메시지를 강조해서 보여주고, "기준 충족 → 정지" 또는 "기각" 중 고른다.
 */
export function AdminReportPanel({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [tab, setTab] = useState<ReportStatus>('PENDING')
  const [reports, setReports] = useState<AdminReport[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [openId, setOpenId] = useState<number | null>(null)
  const [messages, setMessages] = useState<AdminReportMessage[]>([])

  async function load(nextTab: ReportStatus) {
    setLoading(true)
    setError(null)
    const result = await fetchReportsAction(nextTab)
    setLoading(false)
    if (!result.ok) {
      setError(result.message)
      if (result.message.includes('관리자 로그인')) onUnauthorized()
      return
    }
    setReports(result.data)
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load('PENDING')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleTabChange(nextTab: ReportStatus) {
    setTab(nextTab)
    setOpenId(null)
    await load(nextTab)
  }

  async function toggleTranscript(reportId: number) {
    if (openId === reportId) {
      setOpenId(null)
      return
    }
    const result = await fetchReportMessagesAction(reportId)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setMessages(result.data)
    setOpenId(reportId)
  }

  async function handleAction(report: AdminReport) {
    const confirmed = window.confirm(
      `${PARTY_LABEL[report.targetType]} "${report.targetName}"의 이용을 정지할까요?\n` +
        '정지되면 즉시 로그아웃되고 다시 로그인할 수 없습니다. 같은 대상에 대한 다른 대기 신고도 함께 처리됩니다.',
    )
    if (!confirmed) return
    const result = await actionReportAction(report.id)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await load(tab)
  }

  async function handleDismiss(reportId: number) {
    const result = await dismissReportAction(reportId)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await load(tab)
  }

  return (
    <div>
      <div className="flex flex-wrap gap-2">
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

      <p className="mt-4 rounded-lg bg-zinc-50 p-3 text-xs leading-relaxed text-zinc-500 dark:bg-zinc-900">
        신고 기준: <strong className="text-zinc-700 dark:text-zinc-300">욕설·모욕</strong> 또는{' '}
        <strong className="text-zinc-700 dark:text-zinc-300">금전 요구</strong>(개인 계좌 입금, 선입금, 수수료 요구 등).
        대화 원문을 확인해 기준을 충족할 때만 정지 처리하세요.
      </p>

      {error && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      {loading && <p className="mt-6 text-sm text-zinc-500">불러오는 중...</p>}
      {!loading && reports.length === 0 && <p className="mt-6 text-sm text-zinc-500">해당 상태의 신고가 없습니다.</p>}

      <ul className="mt-6 flex flex-col gap-4">
        {reports.map((report) => (
          <li key={report.id} className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-semibold">
                <span className="mr-2 rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-semibold text-red-700 dark:bg-red-500/10 dark:text-red-400">
                  {report.reasonLabel}
                </span>
                {PARTY_LABEL[report.targetType]} {report.targetName}
                {report.targetBlocked && (
                  <span className="ml-2 rounded-full bg-zinc-200 px-2 py-0.5 text-[11px] font-semibold text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
                    정지됨
                  </span>
                )}
              </p>
              <p className="text-xs text-zinc-400">{new Date(report.createdAt).toLocaleString('ko-KR')}</p>
            </div>

            <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-zinc-500">신고 대상</dt>
              <dd>{report.targetEmail ?? '-'} (누적 신고 {report.targetReportCount}건)</dd>
              <dt className="text-zinc-500">신고자</dt>
              <dd>
                {PARTY_LABEL[report.reporterType]} {report.reporterName}
              </dd>
              <dt className="text-zinc-500">상세 내용</dt>
              <dd className="whitespace-pre-wrap">{report.detail ?? '-'}</dd>
            </dl>

            <div className="mt-4 flex flex-wrap items-center gap-2">
              <button
                onClick={() => toggleTranscript(report.id)}
                className="rounded-full border border-zinc-300 px-4 py-1.5 text-sm font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
              >
                {openId === report.id ? '대화 닫기' : '대화 원문 보기'}
              </button>
              {report.status === 'PENDING' && (
                <>
                  <button
                    onClick={() => handleAction(report)}
                    className="rounded-full bg-red-600 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-red-700"
                  >
                    기준 충족 · 이용 정지
                  </button>
                  <button
                    onClick={() => handleDismiss(report.id)}
                    className="rounded-full border border-zinc-300 px-4 py-1.5 text-sm font-semibold text-zinc-600 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
                  >
                    기각
                  </button>
                </>
              )}
            </div>

            {openId === report.id && (
              <div className="mt-4 flex max-h-96 flex-col gap-2 overflow-y-auto rounded-lg bg-zinc-50 p-3 dark:bg-zinc-900">
                {messages.length === 0 && <p className="text-sm text-zinc-500">메시지가 없습니다.</p>}
                {messages.map((message, index) => {
                  const fromTarget = message.senderType === report.targetType
                  return (
                    <div
                      key={index}
                      className={`max-w-[85%] rounded-xl px-3 py-2 text-sm whitespace-pre-wrap ${
                        fromTarget
                          ? 'self-start border border-red-300 bg-white text-zinc-900 dark:border-red-900 dark:bg-zinc-950 dark:text-zinc-100'
                          : 'self-end bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
                      }`}
                    >
                      <p className="mb-0.5 text-[11px] text-zinc-400">
                        {PARTY_LABEL[message.senderType]}
                        {fromTarget && ' (신고 대상)'} · {new Date(message.createdAt).toLocaleString('ko-KR')}
                      </p>
                      {message.content}
                    </div>
                  )
                })}
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
