'use client'

import { useCallback, useEffect, useRef } from 'react'

const WS_URL = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? 'ws://localhost:8080'
const MAX_RETRY_DELAY_MS = 30_000

/**
 * 입장권으로 접속하는 실시간 알림 WebSocket 연결(대화방·변호사 문의함 공통). 알림이 오면 onEvent를 부른다.
 * 로그인 토큰은 httpOnly 쿠키라 브라우저가 직접 쓸 수 없으므로, 매 연결마다 서버에서 1회용
 * 입장권(fetchTicket)을 받아 접속한다. 끊기면 1초→2초→4초…(최대 30초) 간격으로 다시 연결하고,
 * 다시 연결되면 끊긴 동안 놓친 변화를 위해 onEvent를 한 번 부른다.
 */
function useTicketSocket(path: string, fetchTicket: () => Promise<string | null>, onEvent: () => void) {
  const onEventRef = useRef(onEvent)
  useEffect(() => {
    onEventRef.current = onEvent
  })

  useEffect(() => {
    let socket: WebSocket | null = null
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let attempts = 0
    let stopped = false

    function scheduleReconnect() {
      const delay = Math.min(MAX_RETRY_DELAY_MS, 1000 * 2 ** attempts)
      attempts += 1
      retryTimer = setTimeout(connect, delay)
    }

    async function connect() {
      const ticket = await fetchTicket()
      if (stopped) return
      if (!ticket) {
        scheduleReconnect()
        return
      }
      socket = new WebSocket(`${WS_URL}${path}?ticket=${encodeURIComponent(ticket)}`)
      socket.onopen = () => {
        if (attempts > 0) onEventRef.current()
        attempts = 0
      }
      socket.onmessage = () => onEventRef.current()
      socket.onclose = () => {
        socket = null
        if (!stopped) scheduleReconnect()
      }
    }

    connect()
    return () => {
      stopped = true
      clearTimeout(retryTimer)
      socket?.close()
    }
  }, [path, fetchTicket])
}

/**
 * 상담 대화방 WebSocket 연결. 새 메시지 알림이 오면 onMessage를 부른다(화면은 스레드를 다시 불러와
 * 읽음 처리까지 기존 흐름을 탄다).
 */
export function useConsultationSocket(
  consultationId: number,
  fetchTicket: (consultationId: number) => Promise<string | null>,
  onMessage: () => void,
) {
  const fetchRoomTicket = useCallback(() => fetchTicket(consultationId), [fetchTicket, consultationId])
  useTicketSocket('/ws/consultations', fetchRoomTicket, onMessage)
}

/**
 * 상담 목록 WebSocket 연결 (변호사 문의함 /ws/lawyer-inbox, 회원 내 문의 내역 /ws/user-inbox).
 * 내 대화방에 새 문의·새 메시지가 생기면 onChange를 부른다(화면은 목록을 다시 불러와 미리보기·안 읽은 수를 갱신한다).
 */
export function useInboxSocket(
  owner: 'lawyer' | 'user',
  fetchTicket: () => Promise<string | null>,
  onChange: () => void,
) {
  useTicketSocket(owner === 'lawyer' ? '/ws/lawyer-inbox' : '/ws/user-inbox', fetchTicket, onChange)
}
