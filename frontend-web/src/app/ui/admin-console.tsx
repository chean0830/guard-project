'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { AdminLawyerDashboard } from '@/app/ui/admin-lawyer-dashboard'
import { AdminMemberPanel } from '@/app/ui/admin-member-panel'
import { AdminReportPanel } from '@/app/ui/admin-report-panel'

const SECTIONS = [
  { value: 'lawyers', label: '변호사 승인' },
  { value: 'reports', label: '신고 관리' },
  { value: 'members', label: '회원 관리' },
] as const

type Section = (typeof SECTIONS)[number]['value']

/**
 * 관리자 페이지. 일반 로그인 화면(/login)에서 관리자 계정으로 로그인하면 받는 admin_session 쿠키로 접근하며, 쿠키가 없으면
 * proxy.ts가 로그인 화면으로 보낸다. 세션이 만료돼 API가 401을 주면 다시 로그인 화면으로 보낸다.
 */
export function AdminConsole() {
  const router = useRouter()
  const [section, setSection] = useState<Section>('lawyers')

  function handleUnauthorized() {
    router.push('/login?redirect=/admin')
  }

  return (
    <div className="mx-auto w-full max-w-3xl">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">관리자 페이지</h1>
      </div>

      <nav className="mt-6 flex gap-6 border-b border-zinc-200 dark:border-zinc-800">
        {SECTIONS.map((s) => (
          <button
            key={s.value}
            onClick={() => setSection(s.value)}
            className={`-mb-px border-b-2 pb-3 text-sm font-semibold transition-colors ${
              section === s.value
                ? 'border-orange-500 text-zinc-950 dark:text-zinc-50'
                : 'border-transparent text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200'
            }`}
          >
            {s.label}
          </button>
        ))}
      </nav>

      <div className="mt-6">
        {section === 'lawyers' && <AdminLawyerDashboard onUnauthorized={handleUnauthorized} />}
        {section === 'reports' && <AdminReportPanel onUnauthorized={handleUnauthorized} />}
        {section === 'members' && <AdminMemberPanel onUnauthorized={handleUnauthorized} />}
      </div>
    </div>
  )
}
