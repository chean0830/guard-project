'use client'

import { useEffect, useState } from 'react'
import {
  fetchMembersAction,
  setMemberBlockedAction,
  type AdminLawyerMember,
  type AdminUserMember,
  type PartyType,
} from '@/app/lib/admin-moderation-action'

type Member = {
  id: number
  email: string
  name: string | null
  sub: string
  blocked: boolean
  blockedReason: string | null
  reportCount: number
}

const LAWYER_STATUS_LABEL = { PENDING: '승인 대기', APPROVED: '승인됨', REJECTED: '거절됨' } as const

function toMembers(type: PartyType, data: AdminUserMember[] | AdminLawyerMember[]): Member[] {
  if (type === 'USER') {
    return (data as AdminUserMember[]).map((u) => ({
      id: u.id,
      email: u.email,
      name: u.name,
      sub: u.provider === 'LOCAL' ? '이메일 가입' : `${u.provider} 로그인`,
      blocked: u.blocked,
      blockedReason: u.blockedReason,
      reportCount: u.reportCount,
    }))
  }
  return (data as AdminLawyerMember[]).map((l) => ({
    id: l.id,
    email: l.email,
    name: l.name,
    sub: [l.lawFirm, LAWYER_STATUS_LABEL[l.status]].filter(Boolean).join(' · '),
    blocked: l.blocked,
    blockedReason: l.blockedReason,
    reportCount: l.reportCount,
  }))
}

/** 회원/변호사 계정 목록에서 직접 이용 정지·해제하는 화면. 신고 없이도 관리자 직권으로 정지할 수 있다. */
export function AdminMemberPanel({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [type, setType] = useState<PartyType>('USER')
  const [members, setMembers] = useState<Member[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [blockingId, setBlockingId] = useState<number | null>(null)
  const [blockReason, setBlockReason] = useState('')

  async function load(nextType: PartyType) {
    setLoading(true)
    setError(null)
    const result = await fetchMembersAction(nextType)
    setLoading(false)
    if (!result.ok) {
      setError(result.message)
      if (result.message.includes('관리자 로그인')) onUnauthorized()
      return
    }
    setMembers(toMembers(nextType, result.data))
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load('USER')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleTypeChange(nextType: PartyType) {
    setType(nextType)
    setBlockingId(null)
    await load(nextType)
  }

  async function handleBlock(id: number) {
    const result = await setMemberBlockedAction(type, id, true, blockReason)
    setBlockingId(null)
    setBlockReason('')
    if (!result.ok) {
      setError(result.message)
      return
    }
    await load(type)
  }

  async function handleUnblock(member: Member) {
    if (!window.confirm(`${member.name ?? member.email}의 이용 정지를 해제할까요?`)) return
    const result = await setMemberBlockedAction(type, member.id, false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    await load(type)
  }

  const keyword = query.trim().toLowerCase()
  const visible = keyword
    ? members.filter((m) => m.email.toLowerCase().includes(keyword) || (m.name ?? '').toLowerCase().includes(keyword))
    : members

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        {(['USER', 'LAWYER'] as const).map((t) => (
          <button
            key={t}
            onClick={() => handleTypeChange(t)}
            className={`rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
              type === t
                ? 'bg-orange-500 text-white'
                : 'border border-zinc-300 text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800'
            }`}
          >
            {t === 'USER' ? '회원' : '변호사'}
          </button>
        ))}
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="이메일·이름 검색"
          className="min-w-0 flex-1 rounded-full border border-zinc-300 bg-transparent px-4 py-2 text-sm outline-none focus:border-orange-400 dark:border-zinc-700"
        />
      </div>

      {error && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      {loading && <p className="mt-6 text-sm text-zinc-500">불러오는 중...</p>}
      {!loading && visible.length === 0 && <p className="mt-6 text-sm text-zinc-500">표시할 계정이 없습니다.</p>}

      <ul className="mt-6 flex flex-col divide-y divide-zinc-200 rounded-xl border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
        {visible.map((member) => (
          <li key={member.id} className="flex flex-wrap items-center gap-3 p-4">
            <div className="min-w-0 flex-1">
              <p className="font-semibold">
                {member.name ?? member.email}
                {member.blocked && (
                  <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-semibold text-red-700 dark:bg-red-500/10 dark:text-red-400">
                    정지됨
                  </span>
                )}
                {member.reportCount > 0 && (
                  <span className="ml-2 text-xs font-normal text-zinc-400">신고 {member.reportCount}건</span>
                )}
              </p>
              <p className="truncate text-sm text-zinc-500">
                {member.email} · {member.sub}
              </p>
              {member.blocked && member.blockedReason && (
                <p className="text-xs text-zinc-400">정지 사유: {member.blockedReason}</p>
              )}
            </div>

            {member.blocked ? (
              <button
                onClick={() => handleUnblock(member)}
                className="rounded-full border border-zinc-300 px-4 py-1.5 text-sm font-semibold text-zinc-600 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
              >
                정지 해제
              </button>
            ) : blockingId === member.id ? (
              <div className="flex flex-wrap items-center gap-2">
                <input
                  type="text"
                  value={blockReason}
                  onChange={(e) => setBlockReason(e.target.value)}
                  placeholder="정지 사유"
                  className="rounded-lg border border-zinc-300 bg-transparent px-3 py-1.5 text-sm dark:border-zinc-700"
                />
                <button
                  onClick={() => handleBlock(member.id)}
                  className="rounded-full bg-red-600 px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-red-700"
                >
                  정지 확정
                </button>
                <button onClick={() => setBlockingId(null)} className="text-sm text-zinc-500 hover:underline">
                  취소
                </button>
              </div>
            ) : (
              <button
                onClick={() => setBlockingId(member.id)}
                className="rounded-full border border-red-300 px-4 py-1.5 text-sm font-semibold text-red-600 transition-colors hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950"
              >
                이용 정지
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
