'use client'

import { useId, useRef, useState } from 'react'
import { useActionState } from 'react'
import { analyzeAction, type AnalyzeState } from '@/app/lib/analyze-action'
import { AnalyzeResultView } from '@/app/ui/analyze-result'

const initialState: AnalyzeState = { status: 'idle' }

const PROPERTY_TYPES = [
  { value: 'APARTMENT', label: '아파트' },
  { value: 'OFFICETEL', label: '오피스텔' },
  { value: 'VILLA', label: '빌라 (연립·다세대)' },
] as const

const CONTRACT_TYPES = [
  { value: 'JEONSE', label: '전세' },
  { value: 'WOLSE', label: '월세' },
] as const

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

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
            defaultValue="APARTMENT"
            className={inputStyle}
          >
            {PROPERTY_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
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
              <option key={type.value} value={type.value}>
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

        <div className="mt-2 border-t border-dashed border-zinc-200 pt-5 dark:border-zinc-800">
          <p className="mb-4 text-sm text-zinc-500">
            여기부터는 몰라도 괜찮아요. 다만 알려주시면 훨씬 더 정확하게 확인해드릴 수 있어요.
          </p>

          <div className="flex flex-col gap-5">
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

      {state.status === 'success' && <AnalyzeResultView result={state.result} />}
    </div>
  )
}
