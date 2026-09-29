import { AnalyzeForm } from '@/app/ui/analyze-form'

export default function Home() {
  return (
    <div className="flex flex-1 flex-col items-center bg-zinc-50 px-4 py-12 dark:bg-black sm:px-8">
      <main className="flex w-full max-w-2xl flex-col items-center">
        <h1 className="text-2xl font-bold tracking-tight text-zinc-950 dark:text-zinc-50">Project Guard</h1>
        <p className="mt-2 text-center text-sm text-zinc-600 dark:text-zinc-400">
          계약 전 등기부등본을 업로드하면 위험 요소를 쉬운 말로 분석해드립니다.
        </p>

        <div className="mt-8 w-full">
          <AnalyzeForm />
        </div>
      </main>
    </div>
  )
}
