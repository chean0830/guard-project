'use client'

import { useRouter } from 'next/navigation'
import { useCallback } from 'react'
import { getConsultationInboxSocketTicketAction } from '@/app/lib/consultation-action'
import { getLawyerInboxSocketTicketAction } from '@/app/lib/lawyer-consultation-action'
import { useInboxSocket } from '@/app/ui/use-consultation-socket'

/**
 * 상담 목록을 열어 둔 동안 새 문의·새 메시지가 오면 목록(서버 컴포넌트)을 다시 그린다.
 * 화면에는 아무것도 그리지 않는다.
 */
function InboxLive({ owner }: { owner: 'lawyer' | 'user' }) {
  const router = useRouter()
  const refresh = useCallback(() => router.refresh(), [router])
  useInboxSocket(
    owner,
    owner === 'lawyer' ? getLawyerInboxSocketTicketAction : getConsultationInboxSocketTicketAction,
    refresh,
  )
  return null
}

/** 변호사 문의함용. */
export function LawyerInboxLive() {
  return <InboxLive owner="lawyer" />
}

/** 회원 내 문의 내역용. */
export function MemberInboxLive() {
  return <InboxLive owner="user" />
}
