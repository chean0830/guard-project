import { listConsultationsAction } from '@/app/lib/consultation-action'
import { StartConsultationForm } from '@/app/ui/start-consultation-form'
import { ConsultationList } from '@/app/ui/consultation-list'

export default async function ConsultListPage() {
  const consultations = await listConsultationsAction()

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 py-12 sm:px-8">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">변호사 상담</h1>
      <p className="mt-2 text-sm text-zinc-500">
        문의를 남기면 승인된 변호사 중 한 분과 무작위로 연결돼요. 실제 변호사가 직접 확인 후 답변드립니다.
      </p>

      <div className="mt-8 rounded-2xl border border-zinc-200 p-6 dark:border-zinc-800">
        <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">새 문의 시작하기</h2>
        <StartConsultationForm />
      </div>

      {consultations.length > 0 && (
        <div className="mt-10">
          <h2 className="font-semibold text-zinc-950 dark:text-zinc-50">내 문의 내역</h2>
          <ConsultationList consultations={consultations} />
        </div>
      )}
    </div>
  )
}
