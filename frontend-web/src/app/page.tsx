import { AnalyzeForm } from '@/app/ui/analyze-form'
import { BankIcon, CheckBadgeIcon, ScaleIcon, SearchIcon, ShieldIcon, UploadIcon, WarningIcon } from '@/app/ui/icons'
import { LawyerCta } from '@/app/ui/lawyer-cta'

const PAIN_POINTS = [
  '등기부등본을 받아도 무슨 말인지 하나도 모르겠어요',
  '선순위 근저당이 얼마나 있는지 계산이 안 돼요',
  '이 보증금이 시세보다 비싼 건 아닌지 불안해요',
]

const RISK_SCENARIOS = [
  {
    title: '선순위 근저당이 보증금보다 많은 집',
    description: '경매로 넘어가면 순위에서 밀려 보증금을 다 돌려받지 못할 수 있어요.',
  },
  {
    title: '등기부 소유자와 계약서 임대인이 다른 집',
    description: '실제 소유자가 아닌 사람과 계약하면 나중에 계약 자체가 문제 될 수 있어요.',
  },
  {
    title: '시세보다 보증금이 지나치게 높은 집',
    description: '집이 팔려도 보증금을 돌려주기에 부족한 상황이 생길 수 있어요.',
  },
  {
    title: '말소되지 않은 압류·가압류가 남은 집',
    description: '소유권이 넘어가거나 경매에 부쳐질 위험이 이미 걸려 있는 집이에요.',
  },
]

const EVIDENCE_TIERS = [
  {
    badge: '사실 확인',
    badgeClassName: 'bg-zinc-800 text-white dark:bg-zinc-100 dark:text-zinc-900',
    description: '등기부에 기록된 압류·근저당은 판단 없이 있는 그대로 알려드려요.',
  },
  {
    badge: '법적 기준',
    badgeClassName: 'bg-blue-600 text-white',
    description: '주택임대차보호법 시행령 등 실제 법령을 근거로 확인해요.',
  },
  {
    badge: '정부 권고 기준',
    badgeClassName: 'bg-zinc-500 text-white',
    description: '국토교통부·HUG가 제시하는 기준을 참고해요 (법적 구속력은 없어요).',
  },
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

export default async function Home() {
  return (
    <div className="flex flex-1 flex-col bg-white dark:bg-zinc-950">
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-orange-50 to-white px-4 pt-8 pb-24 text-center dark:from-zinc-900 dark:to-zinc-950 sm:px-8">
        <p className="mx-auto mb-4 inline-block rounded-full bg-orange-100 px-4 py-1.5 text-sm font-semibold text-orange-700 dark:bg-orange-500/10 dark:text-orange-400">
          전/월세 계약 전 필수 체크
        </p>
        <h1 className="mx-auto max-w-2xl text-4xl font-extrabold tracking-tight text-zinc-950 sm:text-5xl dark:text-zinc-50">
          계약하기 전,
          <span className="mt-3 block">등기부등본부터 확인하세요</span>
        </h1>
        <p className="mx-auto mt-5 max-w-lg text-base text-zinc-600 sm:text-lg dark:text-zinc-400">
          복잡한 등기부등본을 업로드하면
          <br />
          위험 요소를 쉬운 말로 알려드립니다.
        </p>
        <a
          href="#analyze"
          className="mt-8 inline-flex items-center justify-center rounded-full bg-orange-500 px-8 py-3.5 text-base font-bold text-white transition-transform hover:scale-105 hover:bg-orange-600"
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

      {/* Why it matters: stat */}
      <section className="bg-zinc-950 px-4 py-16 text-center sm:px-8">
        <p className="text-sm font-semibold text-orange-400">전세사기, 이제 남의 일이 아니에요</p>
        <p className="mt-4 text-5xl font-extrabold tracking-tight text-white sm:text-6xl">40,936명</p>
        <p className="mt-3 text-sm text-zinc-400">전세사기 피해자 누적 (국토교통부 발표, 2026년 9월 기준)</p>
        <p className="mx-auto mt-6 max-w-xl text-sm leading-relaxed text-zinc-300 sm:text-base">
          이 중 보증금 3억원 이하 피해가 97.6%를 차지해요. 특별히 비싸지 않은, 평범한 전셋집도
          예외가 아니라는 뜻이에요. &lsquo;나는 괜찮겠지&rsquo;라는 생각이 가장 위험할 수 있어요.
        </p>
      </section>

      {/* Why it matters: risk scenarios */}
      <section className="mx-auto w-full max-w-5xl px-4 py-20 sm:px-8">
        <h2 className="text-center text-2xl font-bold text-zinc-950 sm:text-3xl dark:text-zinc-50">
          이런 집은 특히 조심하세요
        </h2>
        <p className="mt-3 text-center text-sm text-zinc-500 dark:text-zinc-400">
          겉으로는 멀쩡해 보여도, 등기부등본을 확인해봐야 알 수 있어요.
        </p>
        <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {RISK_SCENARIOS.map(({ title, description }) => (
            <div
              key={title}
              className="flex gap-3 rounded-2xl border border-red-200 bg-red-50 p-5 dark:border-red-900/60 dark:bg-red-950/40"
            >
              <WarningIcon className="h-5 w-5 shrink-0 text-red-500" />
              <div>
                <h3 className="font-semibold text-red-950 dark:text-red-100">{title}</h3>
                <p className="mt-1 text-sm leading-relaxed text-red-800/80 dark:text-red-200/70">{description}</p>
              </div>
            </div>
          ))}
        </div>
        <div className="mt-8 flex justify-center">
          <a
            href="#analyze"
            className="inline-flex items-center justify-center rounded-full bg-zinc-950 px-6 py-3 text-sm font-bold text-white transition-transform hover:scale-105 dark:bg-white dark:text-zinc-950"
          >
            내 계약도 확인해보기
          </a>
        </div>
      </section>

      {/* Value props */}
      <section className="mx-auto w-full max-w-5xl px-4 py-20 sm:px-8">
        <h2 className="text-center text-2xl font-bold text-zinc-950 sm:text-3xl dark:text-zinc-50">
          그래서, Project Guard가 이렇게 확인해드려요
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

      {/* Evidence-based trust */}
      <section className="mx-auto w-full max-w-4xl px-4 pb-20 sm:px-8">
        <div className="rounded-2xl border border-zinc-200 bg-zinc-50 p-6 sm:p-8 dark:border-zinc-800 dark:bg-zinc-900/40">
          <h2 className="text-center text-lg font-bold text-zinc-950 sm:text-xl dark:text-zinc-50">
            감이 아니라, 근거로 판단해요
          </h2>
          <p className="mt-1 text-center text-sm text-zinc-500 dark:text-zinc-400">
            위험 신호마다 어떤 근거로 나온 판단인지 함께 보여드려요.
          </p>
          <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
            {EVIDENCE_TIERS.map(({ badge, badgeClassName, description }) => (
              <div key={badge} className="text-center sm:text-left">
                <span className={`inline-block rounded-full px-2.5 py-1 text-xs font-bold ${badgeClassName}`}>
                  {badge}
                </span>
                <p className="mt-2 text-sm leading-relaxed text-zinc-600 dark:text-zinc-400">{description}</p>
              </div>
            ))}
          </div>
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

      {/* Lawyer consultation */}
      <section className="bg-zinc-900 px-4 py-16 text-center sm:px-8">
        <p className="text-xl font-bold text-white sm:text-2xl">
          혹시 보증금(전세금)을 돌려받지 못하고 계신가요? 😥
        </p>
        <p className="mt-3 text-sm text-zinc-400">
          이미 피해를 겪고 계신다면, 등기부등본만으로는 부족할 수 있어요.
          <br />
          Project Guard에 등록된 변호사 중, 전세사기·부동산 사건 경험이 많은 변호사 한 분과
          무작위로 매칭해드려요. ⚖️
        </p>
        <LawyerCta
          label="변호사와 무료로 상담하기 💬"
          className="mt-6 inline-flex items-center justify-center rounded-full bg-orange-500 px-8 py-3.5 text-base font-bold text-white transition-transform hover:scale-105 hover:bg-orange-600"
        />
        <p className="mt-3 text-xs text-zinc-500">
          * 포트폴리오 프로젝트 특성상 응답이 늦어지거나 없을 수 있어요. 급한 경우 대한법률구조공단(국번없이 132)을 이용해주세요.
        </p>
      </section>

      <footer className="border-t border-zinc-200 px-4 py-8 text-center text-xs text-zinc-400 dark:border-zinc-800 sm:px-8">
        <p>Project Guard는 법률 자문이 아닌 참고용 정보를 제공하는 개인 프로젝트입니다.</p>
      </footer>
    </div>
  )
}
