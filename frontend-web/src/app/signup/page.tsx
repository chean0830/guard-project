import { SignupForm } from '@/app/ui/signup-form'

export default async function SignupPage({
  searchParams,
}: {
  searchParams: Promise<{ redirect?: string }>
}) {
  const { redirect } = await searchParams
  return (
    <div className="flex flex-1 items-center justify-center bg-white px-4 py-16 dark:bg-zinc-950">
      <SignupForm redirectTo={redirect ?? '/'} />
    </div>
  )
}
