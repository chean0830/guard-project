'use client'

import { useRouter } from 'next/navigation'
import { useCallback } from 'react'
import { getLawyerInboxSocketTicketAction } from '@/app/lib/lawyer-consultation-action'
import { useLawyerInboxSocket } from '@/app/ui/use-consultation-socket'

/**
 * 문의함을 열어 둔 동안 새 문의·새 메시지가 오면 목록(서버 컴포넌트)을 다시 그린다.
 * 화면에는 아무것도 그리지 않는다.
 */
export function LawyerInboxLive() {
  const router = useRouter()
  const refresh = useCallback(() => router.refresh(), [router])
  useLawyerInboxSocket(getLawyerInboxSocketTicketAction, refresh)
  return null
}
