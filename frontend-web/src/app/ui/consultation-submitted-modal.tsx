'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { subscribeToPushNotifications, type PushSubscribeResult } from '@/app/lib/push-client'

export function ConsultationSubmittedModal({ consultationId }: { consultationId: number }) {
  const router = useRouter()
  const [pushState, setPushState] = useState<'idle' | 'requesting' | PushSubscribeResult>('idle')

  async function handleEnableNotifications() {
    setPushState('requesting')
    const result = await subscribeToPushNotifications()
    setPushState(result)
  }

  function handleGoToThread() {
    router.push(`/consult/${consultationId}`)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 text-center shadow-xl dark:bg-zinc-900">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-orange-100 text-2xl dark:bg-orange-500/10">
          📨
        </div>
        <h2 className="text-lg font-bold text-zinc-950 dark:text-zinc-50">문의가 접수됐어요</h2>
        <p className="mt-2 text-sm leading-relaxed text-zinc-500">
          변호사님이 답변을 준비중이에요.
          <br />
          답변이 오면 알려드릴게요.
        </p>

        {pushState === 'idle' && (
          <button
            type="button"
            onClick={handleEnableNotifications}
            className="mt-5 w-full rounded-full bg-orange-500 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600"
          >
            답변 오면 알림 받기
          </button>
        )}
        {pushState === 'requesting' && <p className="mt-5 text-sm text-zinc-400">알림 권한 요청 중...</p>}
        {pushState === 'subscribed' && (
          <p className="mt-5 rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
            알림을 켰어요. 답변이 오면 바로 알려드릴게요.
          </p>
        )}
        {pushState === 'denied' && (
          <p className="mt-5 text-xs text-zinc-400">알림 권한이 꺼져있어요. 브라우저 설정에서 다시 켤 수 있어요.</p>
        )}
        {(pushState === 'unsupported' || pushState === 'error') && (
          <p className="mt-5 text-xs text-zinc-400">이 브라우저에서는 알림을 지원하지 않아요. 문의함에서 직접 확인해주세요.</p>
        )}

        <button
          type="button"
          onClick={handleGoToThread}
          className="mt-3 w-full rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          대화 보기
        </button>
      </div>
    </div>
  )
}
