import Link from 'next/link'
import { ResetPasswordForm } from '@/app/ui/reset-password-form'

export default async function ResetPasswordPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams
  return (
    <div className="flex flex-1 items-center justify-center bg-white px-4 py-16 dark:bg-zinc-950">
      {token ? (
        <ResetPasswordForm token={token} />
      ) : (
        <p className="text-sm text-zinc-500">
          재설정 링크가 올바르지 않아요.{' '}
          <Link href="/forgot-password" className="font-semibold text-orange-600 hover:underline">
            비밀번호 찾기
          </Link>
          를 다시 요청해주세요.
        </p>
      )}
    </div>
  )
}
