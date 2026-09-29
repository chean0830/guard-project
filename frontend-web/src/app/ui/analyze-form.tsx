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

export function AnalyzeForm() {
  const [state, formAction, pending] = useActionState(analyzeAction, initialState)
  const [files, setFiles] = useState<File[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
  const fileInputId = useId()

  function addFiles(newFiles: FileList | null) {
    if (!newFiles || newFiles.length === 0) return
    setFiles((prev) => [...prev, ...Array.from(newFiles)])
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
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
          <label htmlFor={fileInputId} className="mb-1 block text-sm font-medium">
            등기부등본 (PDF 또는 촬영 사진, 여러 장 가능)
          </label>
          <p className="mb-2 text-xs text-zinc-500">
            PDF 한 개면 그대로 올리고, 사진으로 찍었다면 표제부·갑구·을구가 나온 페이지를 순서대로 모두
            추가해주세요.
          </p>
          <input
            ref={fileInputRef}
            id={fileInputId}
            type="file"
            multiple
            accept="application/pdf,image/*"
            onChange={(e) => addFiles(e.target.files)}
            className="block w-full text-sm file:mr-3 file:rounded-full file:border-0 file:bg-orange-500 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-orange-600"
          />
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
          <label htmlFor="propertyType" className="mb-1 block text-sm font-medium">
            부동산 유형
          </label>
          <select
            id="propertyType"
            name="propertyType"
            required
            defaultValue="APARTMENT"
            className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
          >
            {PROPERTY_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="depositAmount" className="mb-1 block text-sm font-medium">
            보증금 (원)
          </label>
          <input
            id="depositAmount"
            name="depositAmount"
            type="number"
            required
            min={0}
            step={10000}
            placeholder="예: 200000000"
            className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
          />
        </div>

        <div>
          <label htmlFor="buildingName" className="mb-1 block text-sm font-medium">
            단지/건물명 <span className="text-zinc-400">(선택, 시세 조회 정확도 향상)</span>
          </label>
          <input
            id="buildingName"
            name="buildingName"
            type="text"
            placeholder="예: 반포자이"
            className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
          />
        </div>

        <div>
          <label htmlFor="exclusiveAreaSqm" className="mb-1 block text-sm font-medium">
            전용면적 (㎡) <span className="text-zinc-400">(선택, 시세 조회 정확도 향상)</span>
          </label>
          <input
            id="exclusiveAreaSqm"
            name="exclusiveAreaSqm"
            type="number"
            step={0.01}
            min={0}
            placeholder="예: 84.99"
            className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
          />
        </div>

        <div>
          <label htmlFor="declaredLandlordName" className="mb-1 block text-sm font-medium">
            계약서상 임대인 이름 <span className="text-zinc-400">(선택, 등기부 소유자와 대조)</span>
          </label>
          <input
            id="declaredLandlordName"
            name="declaredLandlordName"
            type="text"
            placeholder="예: 홍길동"
            className="w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30"
          />
        </div>

        <button
          type="submit"
          disabled={pending}
          className="mt-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white shadow-md shadow-orange-500/30 transition-all hover:scale-[1.01] hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:scale-100"
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
