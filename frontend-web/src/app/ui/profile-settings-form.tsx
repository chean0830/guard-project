'use client'

import { useActionState } from 'react'
import {
  updateProfileAction,
  changePasswordAction,
  type Profile,
  type ProfileFormState,
} from '@/app/lib/profile-action'

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

export function ProfileSettingsForm({ profile }: { profile: Profile }) {
  const [profileState, profileAction, profilePending] = useActionState(updateProfileAction, initialState)
  const [passwordState, passwordAction, passwordPending] = useActionState(changePasswordAction, initialState)
  const isLocal = profile.provider === 'LOCAL'

  return (
    <div className="flex flex-col gap-10">
      <section>
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">프로필</h2>
        <p className="mt-1 text-xs text-zinc-500">{profile.email}</p>
        <form action={profileAction} className="mt-4 flex flex-col gap-4">
          <div>
            <label htmlFor="name" className="mb-1 block text-sm font-medium">
              표시 이름
            </label>
            <p className="mb-2 text-xs text-zinc-500">변호사와 상담할 때 이메일 대신 보여드릴 이름이에요.</p>
            <input id="name" name="name" type="text" defaultValue={profile.name ?? ''} className={inputStyle} />
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
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">비밀번호 변경</h2>
        {isLocal ? (
          <>
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
          </>
        ) : (
          <p className="mt-4 text-sm text-zinc-500">
            {profile.provider} 계정으로 로그인 중이에요. 소셜 로그인 계정은 비밀번호가 없어 변경할 수 없어요.
          </p>
        )}
      </section>
    </div>
  )
}
