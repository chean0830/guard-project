'use client'

import { useState, useSyncExternalStore } from 'react'

const HIDE_DURATION_MS = 7 * 24 * 60 * 60 * 1000
const CHANGE_EVENT = 'chat-policy-notice-change'

function storageKey(role: 'user' | 'lawyer') {
  return `chat-policy-notice-hidden-until:${role}`
}

// 브라우저 저장소는 사생활 보호 모드 등에서 막혀 있을 수 있어, 실패하면 "숨김 아님"으로 보고 안내를 다시 보여준다.
function isHidden(role: 'user' | 'lawyer'): boolean {
  try {
    const until = Number(window.localStorage.getItem(storageKey(role)))
    return Number.isFinite(until) && until > Date.now()
  } catch {
    return false
  }
}

function subscribe(callback: () => void) {
  window.addEventListener('storage', callback)
  window.addEventListener(CHANGE_EVENT, callback)
  return () => {
    window.removeEventListener('storage', callback)
    window.removeEventListener(CHANGE_EVENT, callback)
  }
}

/**
 * 상담 대화방에 들어올 때 보여주는 이용 안내. 신고 기준(욕설·모욕 / 금전 요구)과 위반 시 이용 정지된다는
 * 것을 회원·변호사 양쪽에 똑같이 알린다. "일주일 동안 안 보기"는 이 브라우저에만 저장된다.
 */
export function ChatPolicyNotice({ role }: { role: 'user' | 'lawyer' }) {
  // 서버 렌더링 시에는 저장소를 볼 수 없으므로 일단 숨겨두고, 브라우저에서 확인한 뒤 띄운다(깜빡임 방지).
  const hidden = useSyncExternalStore(subscribe, () => isHidden(role), () => true)
  const [closed, setClosed] = useState(false)

  if (hidden || closed) return null

  function hideForAWeek() {
    try {
      window.localStorage.setItem(storageKey(role), String(Date.now() + HIDE_DURATION_MS))
    } catch {
      // 저장이 막혀 있어도 이번에는 닫는다.
    }
    setClosed(true)
    window.dispatchEvent(new Event(CHANGE_EVENT))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div
        role="dialog"
        aria-labelledby="chat-policy-title"
        className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl dark:bg-zinc-900"
      >
        <h2 id="chat-policy-title" className="text-lg font-bold text-zinc-950 dark:text-zinc-50">
          상담 이용 안내
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-zinc-500">
          서로 존중하는 상담을 위해 아래 행위는 금지돼요.
        </p>

        <ul className="mt-4 flex flex-col gap-2">
          <li className="rounded-xl border border-zinc-200 p-3 dark:border-zinc-700">
            <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">욕설·모욕</p>
            <p className="text-xs text-zinc-500">욕설, 비하, 인신공격 등 모욕적인 표현</p>
          </li>
          <li className="rounded-xl border border-zinc-200 p-3 dark:border-zinc-700">
            <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">금전 요구</p>
            <p className="text-xs text-zinc-500">개인 계좌 입금, 선입금, 수수료 등 돈을 요구하는 행위</p>
          </li>
        </ul>

        <p className="mt-4 rounded-lg bg-red-50 p-3 text-xs leading-relaxed text-red-800 dark:bg-red-950/40 dark:text-red-200">
          위반하면 상대방이 신고할 수 있고, 관리자가 대화 내용을 확인한 뒤 <strong>이용을 정지</strong>해요.
          정지되면 즉시 로그아웃되고 다시 로그인할 수 없어요.
        </p>
        <p className="mt-2 text-xs text-zinc-400">이런 일을 겪으셨다면 대화방 오른쪽 위 🚨 버튼으로 신고해주세요.</p>

        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={hideForAWeek}
            className="flex-1 rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            일주일 동안 안 보기
          </button>
          <button
            type="button"
            onClick={() => setClosed(true)}
            className="flex-1 rounded-full bg-orange-500 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600"
          >
            확인
          </button>
        </div>
      </div>
    </div>
  )
}
