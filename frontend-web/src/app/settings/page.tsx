import { redirect } from 'next/navigation'
import { listMyBlocksAction, unblockAction } from '@/app/lib/consultation-action'
import { BlockedList } from '@/app/ui/blocked-list'
import { WithdrawSection } from '@/app/ui/withdraw-section'
import { getProfileAction } from '@/app/lib/profile-action'
import { ProfileSettingsForm } from '@/app/ui/profile-settings-form'

export default async function SettingsPage() {
  const profile = await getProfileAction()
  if (!profile) {
    redirect('/login')
  }

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 py-12 sm:px-8">
      <h1 className="mb-8 text-2xl font-bold text-zinc-950 dark:text-zinc-50">설정</h1>
      <ProfileSettingsForm profile={profile} />
      <BlockedList initialEntries={await listMyBlocksAction()} unblock={unblockAction} emptyText="차단한 변호사가 없어요." />
      <WithdrawSection hasPassword={profile.hasPassword} />
    </div>
  )
}
