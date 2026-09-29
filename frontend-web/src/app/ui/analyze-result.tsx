import type {
  AnalyzeResult,
  MortgageEntry,
  OwnershipEntry,
  RiskSignal,
  SeizureEntry,
} from '@/app/lib/analyze-action'
import { CheckBadgeIcon, WarningIcon } from '@/app/ui/icons'

const OWNERSHIP_TYPE_LABEL: Record<OwnershipEntry['type'], string> = {
  OWNERSHIP_PRESERVATION: '소유권보존',
  OWNERSHIP_TRANSFER: '소유권이전',
  OTHER: '기타',
}

const SEIZURE_TYPE_LABEL: Record<SeizureEntry['type'], string> = {
  SEIZURE: '압류',
  PROVISIONAL_SEIZURE: '가압류',
  AUCTION_COMMENCEMENT: '경매개시결정',
  PROVISIONAL_DISPOSITION: '가처분',
}

const SEVERITY_ORDER: Record<RiskSignal['severity'], number> = { HIGH: 0, CAUTION: 1, INFO: 2 }

const SEVERITY_STYLE: Record<
  RiskSignal['severity'],
  { label: string; cardClassName: string; badgeClassName: string; titleClassName: string; showIcon: boolean }
> = {
  HIGH: {
    label: '위험',
    cardClassName:
      'border-l-4 border-red-500 bg-red-50 text-red-950 dark:border-red-500 dark:bg-red-950/50 dark:text-red-50',
    badgeClassName: 'bg-red-600 text-white',
    titleClassName: 'text-base font-bold',
    showIcon: true,
  },
  CAUTION: {
    label: '주의',
    cardClassName:
      'border-l-4 border-amber-500 bg-amber-50 text-amber-950 dark:border-amber-500 dark:bg-amber-950/40 dark:text-amber-50',
    badgeClassName: 'bg-amber-500 text-white',
    titleClassName: 'text-sm font-bold',
    showIcon: true,
  },
  INFO: {
    label: '참고',
    cardClassName:
      'border-l-4 border-zinc-300 bg-zinc-50 text-zinc-800 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200',
    badgeClassName: 'bg-zinc-500 text-white',
    titleClassName: 'text-sm font-medium',
    showIcon: false,
  },
}

const SOURCE_LABEL: Record<RiskSignal['source'], string> = {
  FACTUAL: '등기부 사실 확인',
  LAW: '법적 기준',
  GOVERNMENT_GUIDELINE: '정부 권고 기준(참고용)',
}

function formatWon(amount: number | null): string {
  if (amount === null) return '정보 없음'
  return `${amount.toLocaleString('ko-KR')}원`
}

function EntryRow({ label, cancelled, children }: { label: string; cancelled: boolean; children: React.ReactNode }) {
  return (
    <li className={`rounded-lg border p-3 text-sm ${cancelled ? 'border-zinc-200 opacity-60 dark:border-zinc-800' : 'border-zinc-300 dark:border-zinc-700'}`}>
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium">{label}</span>
        {cancelled && (
          <span className="rounded bg-zinc-200 px-1.5 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
            말소됨
          </span>
        )}
      </div>
      <div className="mt-1 text-zinc-600 dark:text-zinc-400">{children}</div>
    </li>
  )
}

export function AnalyzeResultView({ result }: { result: AnalyzeResult }) {
  const { registry, marketPrice, riskSignals, hasHighRisk, disclaimer } = result
  const sortedSignals = [...riskSignals].sort(
    (a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity],
  )

  return (
    <div className="mt-8 flex flex-col gap-6">
      {hasHighRisk ? (
        <div className="flex flex-col items-center gap-3 rounded-2xl border-2 border-red-500 bg-red-50 p-6 text-center dark:bg-red-950/50">
          <WarningIcon className="h-12 w-12 text-red-600 dark:text-red-500" />
          <p className="text-xl font-extrabold text-red-700 sm:text-2xl dark:text-red-400">
            위험 신호가 발견되었습니다
          </p>
          <p className="text-sm text-red-900/80 dark:text-red-200/80">
            계약을 진행하기 전에, 아래 위험 신호를 꼭 확인해보세요.
          </p>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-emerald-300 bg-emerald-50 p-5 text-center dark:border-emerald-900 dark:bg-emerald-950">
          <CheckBadgeIcon className="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
          <p className="font-semibold text-emerald-900 dark:text-emerald-100">
            중대한 위험 신호는 발견되지 않았습니다
          </p>
        </div>
      )}

      <div className="rounded-lg border border-zinc-300 bg-zinc-50 p-3 text-sm text-zinc-700 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
        {disclaimer}
      </div>

      <section>
        <h2 className="mb-2 text-lg font-semibold">위험 신호</h2>
        {sortedSignals.length === 0 ? (
          <p className="text-sm text-zinc-500">표시할 위험 신호가 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {sortedSignals.map((signal) => {
              const style = SEVERITY_STYLE[signal.severity]
              return (
                <li key={signal.code} className={`rounded-lg p-4 ${style.cardClassName}`}>
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold ${style.badgeClassName}`}
                    >
                      {style.showIcon && <WarningIcon className="h-3.5 w-3.5" />}
                      {style.label}
                    </span>
                    <span className="text-xs opacity-70">{SOURCE_LABEL[signal.source]}</span>
                  </div>
                  <p className={`mt-2 ${style.titleClassName}`}>{signal.title}</p>
                  <p className="mt-1 text-sm leading-relaxed">{signal.detail}</p>
                  <p className="mt-2 text-xs opacity-60">출처: {signal.sourceDescription}</p>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-lg font-semibold">등기부 요약</h2>
        <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-zinc-500">주소</dt>
            <dd>{registry.address ?? '인식 실패'}</dd>
          </div>
          <div>
            <dt className="text-zinc-500">고유번호</dt>
            <dd>{registry.uniqueNumber ?? '인식 실패'}</dd>
          </div>
          <div>
            <dt className="text-zinc-500">조회된 시세</dt>
            <dd>{marketPrice !== null ? formatWon(marketPrice) : '조회 실패 (실거래 내역 없음)'}</dd>
          </div>
          <div>
            <dt className="text-zinc-500">활성 근저당 합계</dt>
            <dd>{formatWon(registry.totalActiveMortgageAmount)}</dd>
          </div>
        </dl>
      </section>

      <section>
        <h3 className="mb-2 font-semibold">소유권 (갑구)</h3>
        {registry.ownershipHistory.length === 0 ? (
          <p className="text-sm text-zinc-500">인식된 소유권 항목이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {registry.ownershipHistory.map((entry) => (
              <EntryRow
                key={entry.rank}
                label={`${entry.rank}번 · ${OWNERSHIP_TYPE_LABEL[entry.type]}`}
                cancelled={entry.cancelled}
              >
                소유자: {entry.ownerName ?? '정보 없음'} · 접수일: {entry.receivedDate ?? '정보 없음'}
              </EntryRow>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="mb-2 font-semibold">근저당 (을구)</h3>
        {registry.mortgages.length === 0 ? (
          <p className="text-sm text-zinc-500">인식된 근저당 항목이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {registry.mortgages.map((entry: MortgageEntry) => (
              <EntryRow key={entry.rank} label={`${entry.rank}번 · 채권최고액 ${formatWon(entry.maxClaimAmount)}`} cancelled={entry.cancelled}>
                채무자: {entry.debtorName ?? '정보 없음'} · 근저당권자: {entry.mortgageeName ?? '정보 없음'} · 접수일:{' '}
                {entry.receivedDate ?? '정보 없음'}
              </EntryRow>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="mb-2 font-semibold">압류/가압류/경매</h3>
        {registry.seizures.length === 0 ? (
          <p className="text-sm text-zinc-500">인식된 압류/가압류/경매 항목이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {registry.seizures.map((entry) => (
              <EntryRow key={entry.rank} label={`${entry.rank}번 · ${SEIZURE_TYPE_LABEL[entry.type]}`} cancelled={entry.cancelled}>
                접수일: {entry.receivedDate ?? '정보 없음'}
              </EntryRow>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
