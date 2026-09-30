'use client'

import { useActionState, useState } from 'react'
import {
  updateLawyerProfileAction,
  updateLawyerNotificationSettingAction,
  changeLawyerPasswordAction,
  type LawyerProfile,
  type ProfileFormState,
} from '@/app/lib/lawyer-profile-action'
import { isLoginRequired, LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const initialState: ProfileFormState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

function StatusMessage({ state }: { state: ProfileFormState }) {
  if (state.status === 'error') {
    if (isLoginRequired(state.message)) return <LoginRequiredDialog message={state.message} />
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

          <div className="mt-2 rounded-xl border border-orange-200 bg-orange-50/50 p-4 dark:border-orange-900/50 dark:bg-orange-950/20">
            <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">회원에게 보여줄 강점</p>
            <p className="mt-1 text-xs leading-relaxed text-zinc-500">
              회원이 변호사를 직접 고를 때 이름 아래에 그대로 보여요. 사실에 근거해 작성해주세요 — 확인되지 않은
              승소율이나 &lsquo;최고&rsquo; 같은 과장 표현은 변호사 광고 규정에 어긋날 수 있어요.
            </p>
            <div className="mt-4 flex flex-col gap-4">
              <div>
                <label htmlFor="headline" className="mb-1 block text-sm font-medium">
                  한 줄 강점
                </label>
                <input
                  id="headline"
                  name="headline"
                  type="text"
                  maxLength={100}
                  placeholder="예: 전세보증금 반환 사건 전문"
                  defaultValue={profile.headline ?? ''}
                  className={inputStyle}
                />
              </div>
              <div>
                <label htmlFor="careerYears" className="mb-1 block text-sm font-medium">
                  경력 (년)
                </label>
                <input
                  id="careerYears"
                  name="careerYears"
                  type="number"
                  min={0}
                  max={70}
                  placeholder="예: 8"
                  defaultValue={profile.careerYears ?? ''}
                  className={inputStyle}
                />
              </div>
              <div>
                <label htmlFor="feeInfo" className="mb-1 block text-sm font-medium">
                  수임료 안내
                </label>
                <input
                  id="feeInfo"
                  name="feeInfo"
                  type="text"
                  maxLength={300}
                  placeholder="예: 첫 상담 무료 · 착수금 100만원부터"
                  defaultValue={profile.feeInfo ?? ''}
                  className={inputStyle}
                />
              </div>
              <div>
                <label htmlFor="achievements" className="mb-1 block text-sm font-medium">
                  주요 실적 <span className="font-normal text-zinc-400">(한 줄에 하나씩)</span>
                </label>
                <textarea
                  id="achievements"
                  name="achievements"
                  rows={4}
                  maxLength={1000}
                  placeholder={'예:\n전세보증금 반환 소송 30건 승소\n임대인 파산 사건 배당 참여'}
                  defaultValue={profile.achievements ?? ''}
                  className={inputStyle}
                />
              </div>
            </div>
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
