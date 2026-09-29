import { getLawyerSessionName, lawyerLogoutAction } from '@/app/lib/lawyer-auth-action'

export default async function LawyerDashboardPage() {
  const name = await getLawyerSessionName()

  return (
    <div className="mx-auto flex w-full max-w-sm flex-1 flex-col items-center justify-center bg-white px-4 py-16 text-center dark:bg-zinc-950">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">
        {name ? `${name} 변호사님, 안녕하세요` : '안녕하세요'}
      </h1>
      <p className="mt-3 text-sm text-zinc-500">
        가입 승인이 완료된 계정입니다. 실제 상담 연결 기능은 아직 준비 중이에요.
      </p>

      <form action={lawyerLogoutAction} className="mt-8">
        <button
          type="submit"
          className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
        >
          로그아웃
        </button>
      </form>
    </div>
  )
}
