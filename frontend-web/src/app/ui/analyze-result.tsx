import type {
  AnalyzeResult,
  MortgageEntry,
  OwnershipEntry,
  RiskSignal,
  SeizureEntry,
} from '@/app/lib/analyze-action'

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

const SEVERITY_STYLE: Record<RiskSignal['severity'], { label: string; className: string }> = {
  HIGH: {
    label: '위험',
    className: 'border-red-300 bg-red-50 text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-100',
  },
  CAUTION: {
    label: '주의',
    className:
      'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100',
  },
  INFO: {
    label: '참고',
    className: 'border-zinc-300 bg-zinc-50 text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100',
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

  return (
    <div className="mt-8 flex flex-col gap-6">
      <div className="rounded-lg border border-zinc-300 bg-zinc-50 p-3 text-sm text-zinc-700 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
        {disclaimer}
      </div>

      <div
        className={`rounded-lg border p-4 ${
          hasHighRisk
            ? 'border-red-300 bg-red-50 dark:border-red-900 dark:bg-red-950'
            : 'border-emerald-300 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950'
        }`}
      >
        <p className={`font-semibold ${hasHighRisk ? 'text-red-900 dark:text-red-100' : 'text-emerald-900 dark:text-emerald-100'}`}>
          {hasHighRisk ? '위험 신호가 발견되었습니다' : '중대한 위험 신호는 발견되지 않았습니다'}
        </p>
      </div>

      <section>
        <h2 className="mb-2 text-lg font-semibold">위험 신호</h2>
        {riskSignals.length === 0 ? (
          <p className="text-sm text-zinc-500">표시할 위험 신호가 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {riskSignals.map((signal) => {
              const style = SEVERITY_STYLE[signal.severity]
              return (
                <li key={signal.code} className={`rounded-lg border p-4 ${style.className}`}>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-black/10 px-2 py-0.5 text-xs font-semibold dark:bg-white/10">
                      {style.label}
                    </span>
                    <span className="text-xs opacity-80">{SOURCE_LABEL[signal.source]}</span>
                  </div>
                  <p className="mt-2 font-semibold">{signal.title}</p>
                  <p className="mt-1 text-sm">{signal.detail}</p>
                  <p className="mt-2 text-xs opacity-70">출처: {signal.sourceDescription}</p>
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
