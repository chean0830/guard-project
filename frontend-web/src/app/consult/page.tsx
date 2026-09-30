import Link from 'next/link'
import { listConsultationsAction } from '@/app/lib/consultation-action'
import { StartConsultationForm } from '@/app/ui/start-consultation-form'
import { ConsultationList } from '@/app/ui/consultation-list'

export default async function ConsultListPage() {
  const consultations = await listConsultationsAction()

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 py-12 sm:px-8">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">변호사 상담</h1>
        <Link href="/consult/payments" className="text-sm text-zinc-500 hover:text-orange-600 hover:underline">
          결제 내역
        </Link>
      </div>
      <p className="mt-2 text-sm text-zinc-500">
        문의를 남기면 승인된 변호사 중 한 분과 무작위로 연결돼요. 실제 변호사가 직접 확인 후 답변드립니다.
      </p>

      <div className="mt-8 rounded-2xl border border-zinc-200 p-6 dark:border-zinc-800">
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">새 문의 시작하기</h2>
        <StartConsultationForm />
      </div>

      <Link
        href="/consult/choose"
        className="group mt-4 flex items-center justify-between gap-4 rounded-2xl border border-orange-200 bg-orange-50/60 p-5 transition-colors hover:bg-orange-50 dark:border-orange-900/50 dark:bg-orange-950/20 dark:hover:bg-orange-950/40"
      >
        <div>
          <p className="font-semibold text-zinc-950 dark:text-zinc-50">랜덤 변호사 말고 내가 원하는 변호사와 매칭되고 싶어요</p>
          <p className="mt-1 text-sm text-zinc-500">입점 변호사 목록을 보고 직접 골라요 · 1회 2,900원</p>
        </div>
        <span className="shrink-0 text-lg text-orange-500 transition-transform group-hover:translate-x-1">→</span>
      </Link>

      {consultations.length > 0 && (
        <div className="mt-10">
          <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">내 문의 내역</h2>
          <ConsultationList consultations={consultations} />
        </div>
      )}
    </div>
  )
}
