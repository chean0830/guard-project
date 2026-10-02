'use client'

import { useId, useRef, useState } from 'react'
import { useActionState } from 'react'
import { analyzeAction, type AnalyzeState } from '@/app/lib/analyze-action'
import { AnalyzeResultView } from '@/app/ui/analyze-result'
import { AnalysisPaymentRequired } from '@/app/ui/analysis-payment-button'
import { LoginRequiredDialog } from '@/app/ui/login-required-dialog'

const initialState: AnalyzeState = { status: 'idle' }

const PROPERTY_TYPES = [
  { value: 'APARTMENT', label: '아파트' },
  { value: 'OFFICETEL', label: '오피스텔' },
  { value: 'VILLA', label: '빌라 (연립·다세대)' },
  { value: 'MULTI_HOUSEHOLD', label: '원룸·다가구주택' },
] as const

type PropertyType = (typeof PROPERTY_TYPES)[number]['value']

const PRIOR_DEPOSIT_SOURCES = [
  { value: '', label: '선택해주세요' },
  { value: 'OFFICIAL_DOCUMENT', label: '전입세대 열람·확정일자 부여현황 서류로 확인했어요' },
  { value: 'LANDLORD_CLAIM', label: '임대인·중개사 말만 들었어요' },
  { value: 'UNKNOWN', label: '아직 모르겠어요' },
] as const

const VIOLATION_BUILDING_ANSWERS = [
  { value: '', label: '아직 확인 안 했어요' },
  { value: 'NOT_MARKED', label: '위반건축물 표시 없음' },
  { value: 'MARKED', label: '위반건축물 표시 있음' },
] as const

// 다가구주택은 먼저 들어온 세입자 보증금이 등기부에 안 나와서, 입력값에만 의존한다는 걸 분명히 알린다.
function MultiHouseholdWarning() {
  return (
    <div
      role="alert"
      className="mt-3 rounded-xl border-2 border-red-400 bg-red-50 p-4 text-sm text-red-950 dark:border-red-800 dark:bg-red-950/50 dark:text-red-50"
    >
      <p className="font-bold">⚠️ 다가구주택은 등기부만으로 안전한지 알 수 없어요</p>
      <ul className="mt-2 list-disc space-y-1 pl-5 leading-relaxed">
        <li>
          건물 전체에 등기부가 하나뿐이라, <b>나보다 먼저 들어온 세입자들의 보증금</b>이 등기부에 나오지 않아요.
          경매로 넘어가면 이 보증금이 내 보증금보다 먼저 배당돼요.
        </li>
        <li>
          그래서 이 결과는 <b>직접 입력하신 선순위 보증금과 건물 시세가 정확하다는 전제</b>에서만 의미가 있어요.
          서비스는 입력값을 확인하지 않고, 결과가 좋아도 &lsquo;안전&rsquo;으로 판정하지 않아요.
        </li>
        <li>
          다가구 전세사기는 임대인이 선순위 보증금을 줄여 말하는 방식으로 자주 일어나요. 임대인 동의를 받아
          주민센터에서 <b>전입세대 열람내역서·확정일자 부여현황</b>을 꼭 직접 확인하세요.
        </li>
      </ul>
      <p className="mt-2 text-xs opacity-80">
        원룸이라도 등기부 첫 줄이 [집합건물]로 시작하면 다가구가 아니라 빌라나 오피스텔이에요. 그 유형으로 골라주세요.
      </p>
    </div>
  )
}

const CONTRACT_TYPES = [
  { value: 'JEONSE', label: '전세' },
  { value: 'WOLSE', label: '월세' },
] as const

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

// 다크 모드에서 글자색(흰색)이 상속돼, 흰 배경으로 펼쳐지는 목록에서 글자가 안 보이는 걸 막는다.
const optionStyle = 'bg-white text-black'

function RequiredBadge() {
  return (
    <span className="rounded-full bg-orange-100 px-2 py-0.5 text-[11px] font-semibold text-orange-600 dark:bg-orange-500/10 dark:text-orange-400">
      필수
    </span>
  )
}

function OptionalBadge() {
  return (
    <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] font-semibold text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
      선택
    </span>
  )
}

function FieldLabel({ htmlFor, required, children }: { htmlFor: string; required: boolean; children: React.ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="mb-1 flex items-center gap-1.5 text-sm font-medium">
      {children}
      {required ? <RequiredBadge /> : <OptionalBadge />}
    </label>
  )
}

export function AnalyzeForm() {
  const [state, formAction, pending] = useActionState(analyzeAction, initialState)
  const [files, setFiles] = useState<File[]>([])
  const [contractType, setContractType] = useState<'JEONSE' | 'WOLSE'>('JEONSE')
  const [propertyType, setPropertyType] = useState<PropertyType>('APARTMENT')
  const [priorDepositSource, setPriorDepositSource] = useState('')
  const [landFiles, setLandFiles] = useState<File[]>([])
  const landFileInputRef = useRef<HTMLInputElement>(null)
  const isMultiHousehold = propertyType === 'MULTI_HOUSEHOLD'
  const fileInputRef = useRef<HTMLInputElement>(null)
  const cameraInputRef = useRef<HTMLInputElement>(null)
  const fileInputId = useId()
  const cameraInputId = useId()

  function addFiles(newFiles: FileList | null) {
    if (!newFiles || newFiles.length === 0) return
    setFiles((prev) => [...prev, ...Array.from(newFiles)])
    if (fileInputRef.current) fileInputRef.current.value = ''
    if (cameraInputRef.current) cameraInputRef.current.value = ''
  }

  function removeFile(index: number) {
    setFiles((prev) => prev.filter((_, i) => i !== index))
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const formData = new FormData(e.currentTarget)
    formData.delete('files')
    for (const file of files) {
      formData.append('files', file)
    }
    formData.delete('landFiles')
    if (isMultiHousehold) {
      for (const file of landFiles) {
        formData.append('landFiles', file)
      }
    }
    formAction(formData)
  }

  return (
    <div className="w-full">
      <div className="mb-6 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100">
        본인이 계약 당사자이거나 본인 명의로 열람 가능한 부동산에 한해 사용해주세요.
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <div>
          <FieldLabel htmlFor={fileInputId} required>
            등기부등본
          </FieldLabel>
          <p className="mb-2 text-xs text-zinc-500">
            PDF는 그대로 올려주시고, 사진으로 찍으셨다면 표제부·갑구·을구가 나온 페이지를 순서대로 담아주세요.
          </p>

          <input
            ref={fileInputRef}
            id={fileInputId}
            type="file"
            multiple
            accept="application/pdf,image/*"
            onChange={(e) => addFiles(e.target.files)}
            className="hidden"
          />
          <input
            ref={cameraInputRef}
            id={cameraInputId}
            type="file"
            accept="image/*"
            capture="environment"
            onChange={(e) => addFiles(e.target.files)}
            className="hidden"
          />

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="rounded-full bg-orange-500 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-orange-600"
            >
              파일에서 선택
            </button>
            <button
              type="button"
              onClick={() => cameraInputRef.current?.click()}
              className="rounded-full border border-orange-500 px-4 py-2 text-sm font-semibold text-orange-600 transition-colors hover:bg-orange-50 dark:text-orange-400 dark:hover:bg-orange-500/10"
            >
              카메라로 촬영
            </button>
          </div>

          {files.length > 0 && (
            <ul className="mt-3 flex flex-col gap-1.5">
              {files.map((file, index) => (
                <li
                  key={`${file.name}-${index}`}
                  className="flex items-center justify-between rounded border border-zinc-200 px-3 py-1.5 text-sm dark:border-zinc-800"
                >
                  <span className="truncate">
                    {index + 1}. {file.name}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    className="ml-3 shrink-0 text-xs text-zinc-500 hover:text-red-600"
                  >
                    삭제
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <FieldLabel htmlFor="propertyType" required>
            부동산 유형
          </FieldLabel>
          <select
            id="propertyType"
            name="propertyType"
            required
            value={propertyType}
            onChange={(e) => setPropertyType(e.target.value as PropertyType)}
            className={inputStyle}
          >
            {PROPERTY_TYPES.map((type) => (
              <option key={type.value} value={type.value} className={optionStyle}>
                {type.label}
              </option>
            ))}
          </select>
          {isMultiHousehold && <MultiHouseholdWarning />}
        </div>

        <div>
          <FieldLabel htmlFor="contractType" required>
            계약 형태
          </FieldLabel>
          <select
            id="contractType"
            name="contractType"
            required
            value={contractType}
            onChange={(e) => setContractType(e.target.value as 'JEONSE' | 'WOLSE')}
            className={inputStyle}
          >
            {CONTRACT_TYPES.map((type) => (
              <option key={type.value} value={type.value} className={optionStyle}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <FieldLabel htmlFor="depositAmount" required>
            보증금 (원)
          </FieldLabel>
          <input
            id="depositAmount"
            name="depositAmount"
            type="number"
            required
            min={0}
            step={10000}
            placeholder="예: 200000000"
            className={inputStyle}
          />
        </div>

        {contractType === 'WOLSE' && (
          <div>
            <FieldLabel htmlFor="monthlyRent" required>
              월세 (원)
            </FieldLabel>
            <p className="mb-2 text-xs text-zinc-500">
              월세를 법정 전환율로 보증금에 환산해서, 전세와 같은 기준으로 비교해드려요.
            </p>
            <input
              id="monthlyRent"
              name="monthlyRent"
              type="number"
              required
              min={0}
              step={10000}
              placeholder="예: 500000"
              className={inputStyle}
            />
          </div>
        )}

        {isMultiHousehold && (
          <div className="flex flex-col gap-5 rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
            <div>
              <FieldLabel htmlFor="priorDepositSource" required>
                먼저 들어온 세입자 보증금, 어떻게 확인하셨나요?
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                서류로 확인하지 않았다면 계산 결과와 상관없이 위험으로 표시해드려요.
              </p>
              <select
                id="priorDepositSource"
                name="priorDepositSource"
                required
                value={priorDepositSource}
                onChange={(e) => setPriorDepositSource(e.target.value)}
                className={inputStyle}
              >
                {PRIOR_DEPOSIT_SOURCES.map((source) => (
                  <option
                    key={source.value}
                    value={source.value}
                    disabled={source.value === ''}
                    className={optionStyle}
                  >
                    {source.label}
                  </option>
                ))}
              </select>
            </div>

            {priorDepositSource !== 'UNKNOWN' && (
              <div>
                <FieldLabel htmlFor="priorDepositTotal" required>
                  먼저 들어온 세입자 보증금 합계 (원)
                </FieldLabel>
                <p className="mb-2 text-xs text-zinc-500">
                  나보다 먼저 전입·확정일자를 받은 세입자들의 보증금을 모두 더한 금액이에요. 없으면 0을 입력하세요.
                </p>
                <input
                  id="priorDepositTotal"
                  name="priorDepositTotal"
                  type="number"
                  required
                  min={0}
                  step={10000}
                  placeholder="예: 300000000"
                  className={inputStyle}
                />
              </div>
            )}

            <div>
              <FieldLabel htmlFor="roomCount" required={false}>
                건물 전체 방(호실) 수
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                비워두시면 건축물대장 가구수로 계산해요. 실제 방이 더 많아 보이면(방 쪼개기) 직접 세어서 입력해주세요.
                나중에 들어올 소액임차인이 먼저 받아갈 수 있는 금액을 계산하는 데 써요.
              </p>
              <input
                id="roomCount"
                name="roomCount"
                type="number"
                min={1}
                step={1}
                placeholder="예: 8"
                className={inputStyle}
              />
            </div>

            <div>
              <FieldLabel htmlFor="landFiles" required={false}>
                토지 등기부등본
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                다가구주택은 건물과 토지 등기부가 따로 있어요. 토지 등기부도 올리시면 토지에만 걸린 근저당·압류와 토지
                소유자가 건물 소유자와 같은지까지 확인해드려요.
              </p>
              <input
                ref={landFileInputRef}
                id="landFiles"
                type="file"
                multiple
                accept="application/pdf,image/*"
                onChange={(e) => {
                  const chosen = e.target.files
                  if (chosen && chosen.length > 0) setLandFiles((prev) => [...prev, ...Array.from(chosen)])
                  if (landFileInputRef.current) landFileInputRef.current.value = ''
                }}
                className="hidden"
              />
              <button
                type="button"
                onClick={() => landFileInputRef.current?.click()}
                className="rounded-full border border-orange-500 px-4 py-2 text-sm font-semibold text-orange-600 transition-colors hover:bg-orange-50 dark:text-orange-400 dark:hover:bg-orange-500/10"
              >
                토지 등기부 선택
              </button>
              {landFiles.length > 0 && (
                <ul className="mt-3 flex flex-col gap-1.5">
                  {landFiles.map((file, index) => (
                    <li
                      key={`${file.name}-${index}`}
                      className="flex items-center justify-between rounded-lg border border-zinc-200 px-3 py-2 text-sm dark:border-zinc-800"
                    >
                      <span className="truncate">
                        {index + 1}. {file.name}
                      </span>
                      <button
                        type="button"
                        onClick={() => setLandFiles((prev) => prev.filter((_, i) => i !== index))}
                        className="ml-3 shrink-0 text-xs text-zinc-500 hover:text-red-600"
                      >
                        삭제
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div>
              <FieldLabel htmlFor="buildingPrice" required={false}>
                건물 전체 시세 (원)
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                다가구는 실거래가로 이 건물 시세를 찾을 수 없어 직접 입력받아요. 비워두시면 공시가격으로 계산하는데, 공시가격은
                보통 실제 시세보다 낮아 보수적인 결과가 나와요.
              </p>
              <input
                id="buildingPrice"
                name="buildingPrice"
                type="number"
                min={0}
                step={10000}
                placeholder="예: 1500000000"
                className={inputStyle}
              />
            </div>

            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" name="multiHouseholdAcknowledged" required className="mt-1 accent-orange-500" />
              <span>
                결과가 입력한 값에 따라 달라지고, 서비스가 입력값을 확인하지 않는다는 점을 이해했어요.
              </span>
            </label>
          </div>
        )}

        <div className="mt-2 border-t border-dashed border-zinc-200 pt-5 dark:border-zinc-800">
          <p className="mb-4 text-sm text-zinc-500">
            여기부터는 몰라도 괜찮아요. 다만 알려주시면 훨씬 더 정확하게 확인해드릴 수 있어요.
          </p>

          <div className="flex flex-col gap-5">
            {!isMultiHousehold && (
              <>
                <div>
                  <FieldLabel htmlFor="buildingName" required={false}>
                    단지/건물명
                  </FieldLabel>
                  <p className="mb-2 text-xs text-zinc-500">단지명까지 알려주시면 시세를 더 정확하게 찾아드릴 수 있어요.</p>
                  <input id="buildingName" name="buildingName" type="text" placeholder="예: 반포자이" className={inputStyle} />
                </div>

                <div>
                  <FieldLabel htmlFor="exclusiveAreaSqm" required={false}>
                    전용면적 (㎡)
                  </FieldLabel>
                  <p className="mb-2 text-xs text-zinc-500">전용면적까지 알려주시면 같은 평수 거래만 골라서 비교해드려요.</p>
                  <input
                    id="exclusiveAreaSqm"
                    name="exclusiveAreaSqm"
                    type="number"
                    step={0.01}
                    min={0}
                    placeholder="예: 84.99"
                    className={inputStyle}
                  />
                </div>
              </>
            )}

            <div>
              <FieldLabel htmlFor="declaredLandlordName" required={false}>
                계약서상 임대인 이름
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                임대인 이름까지 적어주시면 등기부상 소유자와 같은 사람인지 확인해드려요.
              </p>
              <input
                id="declaredLandlordName"
                name="declaredLandlordName"
                type="text"
                placeholder="예: 홍길동"
                className={inputStyle}
              />
            </div>

            <div>
              <FieldLabel htmlFor="declaredAddress" required={false}>
                계약서상 주소
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                계약서에 적힌 주소까지 알려주시면 등기부 주소와 같은 곳인지 확인해드려요.
              </p>
              <input
                id="declaredAddress"
                name="declaredAddress"
                type="text"
                placeholder="예: 서울특별시 강남구 테스트로 123 101동 501호"
                className={inputStyle}
              />
            </div>

            <div>
              <FieldLabel htmlFor="violationBuilding" required={false}>
                건축물대장 위반건축물 표시
              </FieldLabel>
              <p className="mb-2 text-xs text-zinc-500">
                정부24에서 건축물대장을 무료로 열람하면 첫 장 위쪽에 &lsquo;위반건축물&rsquo; 표시가 있는지 볼 수 있어요.
                이 정보는 공공 API로 받을 수 없어 직접 확인해주셔야 해요.
              </p>
              <select id="violationBuilding" name="violationBuilding" defaultValue="" className={inputStyle}>
                {VIOLATION_BUILDING_ANSWERS.map((answer) => (
                  <option key={answer.value} value={answer.value} className={optionStyle}>
                    {answer.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <button
          type="submit"
          disabled={pending}
          className="mt-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-all hover:scale-[1.01] hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:scale-100"
        >
          {pending ? '분석 중...' : '분석하기'}
        </button>
      </form>

      {state.status === 'error' && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {state.message}
        </p>
      )}

      <LoginRequiredDialog message={state.status === 'error' ? state.message : null} />
      {state.status === 'payment_required' && <AnalysisPaymentRequired message={state.message} loggedIn={state.loggedIn} />}
      {state.status === 'success' && <AnalyzeResultView result={state.result} />}
    </div>
  )
}
