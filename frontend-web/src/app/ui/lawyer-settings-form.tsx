'use client'

import { useActionState, useState } from 'react'
import {
  updateLawyerProfileAction,
  updateLawyerNotificationSettingAction,
  changeLawyerPasswordAction,
  type LawyerProfile,
  type ProfileFormState,
} from '@/app/lib/lawyer-profile-action'

const initialState: ProfileFormState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

function StatusMessage({ state }: { state: ProfileFormState }) {
  if (state.status === 'error') {
    return (
      <p className="mt-3 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
        {state.message}
      </p>
    )
  }
  if (state.status === 'success') {
    return (
      <p className="mt-3 rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
        저장되었습니다.
      </p>
    )
  }
  return null
}

export function LawyerSettingsForm({ profile }: { profile: LawyerProfile }) {
  const [profileState, profileAction, profilePending] = useActionState(updateLawyerProfileAction, initialState)
  const [passwordState, passwordAction, passwordPending] = useActionState(changeLawyerPasswordAction, initialState)
  const [notificationsEnabled, setNotificationsEnabled] = useState(profile.emailNotificationsEnabled)
  const [savingNotifications, setSavingNotifications] = useState(false)

  async function handleToggleNotifications() {
    const next = !notificationsEnabled
    setNotificationsEnabled(next)
    setSavingNotifications(true)
    await updateLawyerNotificationSettingAction(next)
    setSavingNotifications(false)
  }

  return (
    <div className="flex flex-col gap-10">
      <section>
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">프로필</h2>
        <p className="mt-1 text-xs text-zinc-500">
          이메일({profile.email})과 등록번호({profile.barNumber})는 승인된 값이라 직접 바꿀 수 없어요.
        </p>
        <form action={profileAction} className="mt-4 flex flex-col gap-4">
          <div>
            <label htmlFor="name" className="mb-1 block text-sm font-medium">
              이름
            </label>
            <input id="name" name="name" type="text" required defaultValue={profile.name} className={inputStyle} />
          </div>
          <div>
            <label htmlFor="lawFirm" className="mb-1 block text-sm font-medium">
              소속
            </label>
            <input id="lawFirm" name="lawFirm" type="text" defaultValue={profile.lawFirm ?? ''} className={inputStyle} />
          </div>
          <div>
            <label htmlFor="specialties" className="mb-1 block text-sm font-medium">
              전문 분야
            </label>
            <input
              id="specialties"
              name="specialties"
              type="text"
              placeholder="예: 전세사기, 임대차분쟁"
              defaultValue={profile.specialties ?? ''}
              className={inputStyle}
            />
          </div>
          <div>
            <label htmlFor="introduction" className="mb-1 block text-sm font-medium">
              소개
            </label>
            <textarea
              id="introduction"
              name="introduction"
              rows={3}
              defaultValue={profile.introduction ?? ''}
              className={inputStyle}
            />
          </div>
          <button
            type="submit"
            disabled={profilePending}
            className="self-start rounded-full bg-orange-500 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {profilePending ? '저장 중...' : '프로필 저장'}
          </button>
        </form>
        <StatusMessage state={profileState} />
      </section>

      <section>
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">알림</h2>
        <label className="mt-4 flex items-center justify-between rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
          <span className="text-sm">새 문의가 오면 등록 이메일로 알림 받기</span>
          <input
            type="checkbox"
            checked={notificationsEnabled}
            disabled={savingNotifications}
            onChange={handleToggleNotifications}
            className="h-5 w-5 accent-orange-500"
          />
        </label>
      </section>

      <section>
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">비밀번호 변경</h2>
        <form action={passwordAction} className="mt-4 flex flex-col gap-4">
          <div>
            <label htmlFor="currentPassword" className="mb-1 block text-sm font-medium">
              현재 비밀번호
            </label>
            <input id="currentPassword" name="currentPassword" type="password" required className={inputStyle} />
          </div>
          <div>
            <label htmlFor="newPassword" className="mb-1 block text-sm font-medium">
              새 비밀번호
            </label>
            <input id="newPassword" name="newPassword" type="password" required minLength={8} className={inputStyle} />
          </div>
          <button
            type="submit"
            disabled={passwordPending}
            className="self-start rounded-full border border-zinc-300 px-5 py-2.5 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            {passwordPending ? '변경 중...' : '비밀번호 변경'}
          </button>
        </form>
        <StatusMessage state={passwordState} />
      </section>
    </div>
  )
}
