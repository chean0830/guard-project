import { LawyerLoginForm } from '@/app/ui/lawyer-login-form'

export default async function LawyerLoginPage({ searchParams }: { searchParams: Promise<{ redirect?: string }> }) {
  const { redirect } = await searchParams
  return (
    <div className="flex flex-1 items-center justify-center bg-white px-4 py-16 dark:bg-zinc-950">
      <LawyerLoginForm redirectTo={redirect} />
    </div>
  )
}
