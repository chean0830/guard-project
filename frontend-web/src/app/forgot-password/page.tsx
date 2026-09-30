import { ForgotPasswordForm } from '@/app/ui/forgot-password-form'

export default async function ForgotPasswordPage({ searchParams }: { searchParams: Promise<{ type?: string }> }) {
  const { type } = await searchParams
  return (
    <div className="flex flex-1 items-center justify-center bg-white px-4 py-16 dark:bg-zinc-950">
      <ForgotPasswordForm lawyer={type === 'lawyer'} />
    </div>
  )
}
