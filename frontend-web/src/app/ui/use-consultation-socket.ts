'use client'

import { useEffect, useRef } from 'react'

const WS_URL = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? 'ws://localhost:8080'
const MAX_RETRY_DELAY_MS = 30_000

/**
 * 상담 대화방 WebSocket 연결. 새 메시지 알림이 오면 onMessage를 부른다(화면은 스레드를 다시 불러와
 * 읽음 처리까지 기존 흐름을 탄다). 로그인 토큰은 httpOnly 쿠키라 브라우저가 직접 쓸 수 없으므로,
 * 매 연결마다 서버에서 1회용 입장권(fetchTicket)을 받아 접속한다. 끊기면 1초→2초→4초…(최대 30초)
 * 간격으로 다시 연결하고, 다시 연결되면 끊긴 동안 놓친 메시지를 위해 한 번 새로 불러온다.
 */
export function useConsultationSocket(
  consultationId: number,
  fetchTicket: (consultationId: number) => Promise<string | null>,
  onMessage: () => void,
) {
  const onMessageRef = useRef(onMessage)
  useEffect(() => {
    onMessageRef.current = onMessage
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
      const ticket = await fetchTicket(consultationId)
      if (stopped) return
      if (!ticket) {
        scheduleReconnect()
        return
      }
      socket = new WebSocket(`${WS_URL}/ws/consultations?ticket=${encodeURIComponent(ticket)}`)
      socket.onopen = () => {
        if (attempts > 0) onMessageRef.current()
        attempts = 0
      }
      socket.onmessage = () => onMessageRef.current()
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
  }, [consultationId, fetchTicket])
}
