import Link from 'next/link'
import { redirect } from 'next/navigation'
import { getLawyerProfileAction } from '@/app/lib/lawyer-profile-action'
import { LawyerSettingsForm } from '@/app/ui/lawyer-settings-form'

export default async function LawyerSettingsPage() {
  const profile = await getLawyerProfileAction()
  if (!profile) {
    redirect('/lawyer/login')
  }

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 py-12 sm:px-8">
      <Link href="/lawyer" className="mb-4 text-sm text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300">
        ← 마이페이지
      </Link>
      <h1 className="mb-8 text-2xl font-bold text-zinc-950 dark:text-zinc-50">설정</h1>
      <LawyerSettingsForm profile={profile} />
    </div>
  )
}
