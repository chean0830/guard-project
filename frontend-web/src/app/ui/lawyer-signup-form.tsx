'use client'

import Link from 'next/link'
import { useId, useRef, useState } from 'react'
import { useActionState } from 'react'
import { lawyerSignupAction, type LawyerSignupState } from '@/app/lib/lawyer-auth-action'

const initialState: LawyerSignupState = { status: 'idle' }

const inputStyle =
  'w-full rounded-xl border border-zinc-300 bg-transparent px-3.5 py-2.5 text-sm outline-none transition-colors focus:border-orange-400 focus:ring-2 focus:ring-orange-100 dark:border-zinc-700 dark:focus:ring-orange-900/30'

export function LawyerSignupForm() {
  const [state, formAction, pending] = useActionState(lawyerSignupAction, initialState)
  const [documents, setDocuments] = useState<File[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
  const fileInputId = useId()

  function addFiles(newFiles: FileList | null) {
    if (!newFiles || newFiles.length === 0) return
    setDocuments((prev) => [...prev, ...Array.from(newFiles)])
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  function removeFile(index: number) {
    setDocuments((prev) => prev.filter((_, i) => i !== index))
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const formData = new FormData(e.currentTarget)
    formData.delete('documents')
    for (const file of documents) {
      formData.append('documents', file)
    }
    formAction(formData)
  }

  if (state.status === 'success') {
    return (
      <div className="mx-auto w-full max-w-sm text-center">
        <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">제출 완료</h1>
        <p className="mt-4 rounded-xl border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-100">
          {state.message}
        </p>
        <Link
          href="/lawyer/login"
          className="mt-6 inline-block rounded-full bg-orange-500 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-orange-600"
        >
          로그인 화면으로
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-bold text-zinc-950 dark:text-zinc-50">변호사 회원가입</h1>
      <p className="mt-2 text-sm text-zinc-500">
        변호사 자격을 확인할 수 있는 서류를 제출해주세요. 관리자 검수 후 승인되면 로그인하실 수 있어요.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium">
            이메일
          </label>
          <input id="email" name="email" type="email" required autoComplete="email" className={inputStyle} />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium">
            비밀번호
          </label>
          <input
            id="password"
            name="password"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            className={inputStyle}
          />
          <p className="mt-1 text-xs text-zinc-500">8자 이상으로 입력해주세요.</p>
        </div>
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium">
            이름
          </label>
          <input id="name" name="name" type="text" required placeholder="예: 김변호" className={inputStyle} />
        </div>
        <div>
          <label htmlFor="lawFirm" className="mb-1 block text-sm font-medium">
            소속 (선택)
          </label>
          <input id="lawFirm" name="lawFirm" type="text" placeholder="예: 법무법인 테스트" className={inputStyle} />
        </div>
        <div>
          <label htmlFor="barNumber" className="mb-1 block text-sm font-medium">
            변호사 등록번호
          </label>
          <input id="barNumber" name="barNumber" type="text" required placeholder="대한변호사협회 등록번호" className={inputStyle} />
        </div>

        <div>
          <label htmlFor={fileInputId} className="mb-1 block text-sm font-medium">
            자격 증명 서류
          </label>
          <p className="mb-2 text-xs text-zinc-500">변호사 자격증 사본, 신분증 등 자격을 확인할 수 있는 서류를 첨부해주세요.</p>

          <input
            ref={fileInputRef}
            id={fileInputId}
            type="file"
            multiple
            accept=".pdf,.jpg,.jpeg,application/pdf,image/jpeg"
            onChange={(e) => addFiles(e.target.files)}
            className="hidden"
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="rounded-full border border-orange-500 px-4 py-2 text-sm font-semibold text-orange-600 transition-colors hover:bg-orange-50 dark:text-orange-400 dark:hover:bg-orange-500/10"
          >
            서류 첨부
          </button>

          {documents.length > 0 && (
            <ul className="mt-3 flex flex-col gap-1.5">
              {documents.map((file, index) => (
                <li
                  key={`${file.name}-${index}`}
                  className="flex items-center justify-between rounded border border-zinc-200 px-3 py-1.5 text-sm dark:border-zinc-800"
                >
                  <span className="truncate">{file.name}</span>
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

        <button
          type="submit"
          disabled={pending}
          className="mt-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? '제출 중...' : '가입 신청'}
        </button>
      </form>

      {state.status === 'error' && (
        <p className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {state.message}
        </p>
      )}

      <p className="mt-6 text-center text-sm text-zinc-500">
        이미 승인된 계정이 있으신가요?{' '}
        <Link href="/lawyer/login" className="font-semibold text-orange-600 hover:underline dark:text-orange-400">
          변호사 로그인
        </Link>
      </p>
    </div>
  )
}
