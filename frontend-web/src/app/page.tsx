import { AnalyzeForm } from '@/app/ui/analyze-form'
import { BankIcon, CheckBadgeIcon, ScaleIcon, SearchIcon, ShieldIcon, UploadIcon } from '@/app/ui/icons'

const PAIN_POINTS = [
  '등기부등본을 받아도 무슨 말인지 하나도 모르겠어요',
  '선순위 근저당이 얼마나 있는지 계산이 안 돼요',
  '이 보증금이 시세보다 비싼 건 아닌지 불안해요',
]

const VALUE_PROPS = [
  {
    icon: BankIcon,
    title: '실거래가 비교',
    description: '국토교통부 실거래가와 보증금을 비교해 전세가율이 안전한 수준인지 확인해요.',
  },
  {
    icon: ShieldIcon,
    title: '근저당·압류 확인',
    description: '등기부에 남아있는 근저당권, 압류, 가압류, 경매개시결정을 자동으로 찾아드려요.',
  },
  {
    icon: ScaleIcon,
    title: '법적 보호 기준 안내',
    description: '주택임대차보호법상 소액임차인 최우선변제 대상인지, 지역 기준과 함께 알려드려요.',
  },
]

const STEPS = [
  {
    icon: UploadIcon,
    title: '등기부등본 업로드',
    description: 'PDF 또는 촬영한 사진을 올려주세요. 여러 장으로 나눠 찍었어도 괜찮아요.',
  },
  {
    icon: SearchIcon,
    title: '자동 분석',
    description: '등기부 내용과 실거래가를 대조해 위험 신호가 있는지 확인해요.',
  },
  {
    icon: CheckBadgeIcon,
    title: '결과 확인',
    description: '위험 등급과 그 이유를 쉬운 말로 정리해서 보여드려요.',
  },
]

export default function Home() {
  return (
    <div className="flex flex-1 flex-col bg-white dark:bg-zinc-950">
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-orange-50 to-white px-4 pt-20 pb-24 text-center dark:from-zinc-900 dark:to-zinc-950 sm:px-8">
        <p className="mx-auto mb-4 inline-block rounded-full bg-orange-100 px-4 py-1.5 text-sm font-semibold text-orange-700 dark:bg-orange-500/10 dark:text-orange-400">
          전/월세 계약 전 필수 체크
        </p>
        <h1 className="mx-auto max-w-2xl text-4xl font-extrabold tracking-tight text-zinc-950 sm:text-5xl dark:text-zinc-50">
          계약하기 전,
          <br />
          등기부등본부터 확인하세요
        </h1>
        <p className="mx-auto mt-5 max-w-lg text-base text-zinc-600 sm:text-lg dark:text-zinc-400">
          복잡한 등기부등본을 업로드하면 위험 요소를 쉬운 말로 알려드립니다.
        </p>
        <a
          href="#analyze"
          className="mt-8 inline-flex items-center justify-center rounded-full bg-orange-500 px-8 py-3.5 text-base font-bold text-white shadow-lg shadow-orange-500/30 transition-transform hover:scale-105 hover:bg-orange-600"
        >
          무료로 확인하기
        </a>

        <ul className="mx-auto mt-14 flex max-w-2xl flex-col gap-2 text-sm text-zinc-500 dark:text-zinc-400">
          {PAIN_POINTS.map((point) => (
            <li key={point} className="flex items-center justify-center gap-2">
              <span aria-hidden className="text-orange-400">
                ·
              </span>
              {point}
            </li>
          ))}
        </ul>
      </section>

      {/* Value props */}
      <section className="mx-auto w-full max-w-5xl px-4 py-20 sm:px-8">
        <h2 className="text-center text-2xl font-bold text-zinc-950 sm:text-3xl dark:text-zinc-50">
          Project Guard가 확인해드려요
        </h2>
        <div className="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-3">
          {VALUE_PROPS.map(({ icon: Icon, title, description }) => (
            <div
              key={title}
              className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900"
            >
              <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-full bg-orange-100 text-orange-600 dark:bg-orange-500/10 dark:text-orange-400">
                <Icon />
              </div>
              <h3 className="font-semibold text-zinc-950 dark:text-zinc-50">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-zinc-600 dark:text-zinc-400">{description}</p>
            </div>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section className="bg-zinc-50 px-4 py-20 sm:px-8 dark:bg-zinc-900/40">
        <div className="mx-auto max-w-5xl">
          <h2 className="text-center text-2xl font-bold text-zinc-950 sm:text-3xl dark:text-zinc-50">
            이용 방법은 간단해요
          </h2>
          <div className="mt-10 grid grid-cols-1 gap-8 sm:grid-cols-3">
            {STEPS.map(({ icon: Icon, title, description }, index) => (
              <div key={title} className="flex flex-col items-center text-center">
                <div className="relative mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-white text-orange-600 shadow-md dark:bg-zinc-800 dark:text-orange-400">
                  <Icon className="h-7 w-7" />
                  <span className="absolute -top-1.5 -right-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-orange-500 text-xs font-bold text-white">
                    {index + 1}
                  </span>
                </div>
                <h3 className="font-semibold text-zinc-950 dark:text-zinc-50">{title}</h3>
                <p className="mt-2 max-w-xs text-sm leading-relaxed text-zinc-600 dark:text-zinc-400">
                  {description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Analyze form */}
      <section id="analyze" className="scroll-mt-8 px-4 py-20 sm:px-8">
        <div className="mx-auto flex w-full max-w-2xl flex-col items-center">
          <h2 className="text-center text-2xl font-bold text-zinc-950 sm:text-3xl dark:text-zinc-50">
            지금 바로 확인해보세요
          </h2>
          <p className="mt-2 text-center text-sm text-zinc-500 dark:text-zinc-400">
            업로드한 파일은 분석 후 즉시 삭제되며 서버에 저장되지 않습니다.
          </p>
          <div className="mt-8 w-full rounded-3xl border border-zinc-200 bg-white p-6 shadow-xl shadow-zinc-200/50 sm:p-8 dark:border-zinc-800 dark:bg-zinc-900 dark:shadow-none">
            <AnalyzeForm />
          </div>
        </div>
      </section>

      <footer className="border-t border-zinc-200 px-4 py-8 text-center text-xs text-zinc-400 dark:border-zinc-800 sm:px-8">
        <p>Project Guard는 법률 자문이 아닌 참고용 정보를 제공하는 개인 프로젝트입니다.</p>
      </footer>
    </div>
  )
}
