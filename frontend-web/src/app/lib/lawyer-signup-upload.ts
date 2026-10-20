import type { LawyerSignupState } from '@/app/lib/lawyer-auth-action'
import { fileSizeProblem, PUBLIC_BACKEND_URL, UPLOAD_FAILED_MESSAGE } from '@/app/lib/analyze-upload'

/**
 * 변호사 가입 신청. 자격 서류(사진·PDF)를 브라우저가 백엔드로 바로 올린다 — Next.js 서버를 거치면
 * 요청 한도(Server Action 1MB, Vercel 4.5MB)에 걸린다. 가입 신청은 로그인 전이라 입장권이 필요 없다.
 */
export async function submitLawyerSignup(
  _prevState: LawyerSignupState,
  formData: FormData,
): Promise<LawyerSignupState> {
  const documents = formData.getAll('documents').filter((f): f is File => f instanceof File && f.size > 0)
  if (documents.length === 0) {
    return { status: 'error', message: '변호사 자격을 확인할 수 있는 서류를 1개 이상 첨부해주세요.' }
  }
  const sizeProblem = fileSizeProblem(documents)
  if (sizeProblem) {
    return { status: 'error', message: sizeProblem }
  }

  const outgoing = new FormData()
  for (const field of ['email', 'password', 'name', 'lawFirm', 'barNumber']) {
    outgoing.append(field, String(formData.get(field) ?? ''))
  }
  for (const document of documents) {
    outgoing.append('documents', document)
  }

  let response: Response
  try {
    response = await fetch(`${PUBLIC_BACKEND_URL}/api/lawyer/auth/signup`, {
      method: 'POST',
      body: outgoing,
    })
  } catch {
    return { status: 'error', message: UPLOAD_FAILED_MESSAGE }
  }

  if (!response.ok) {
    const message = await response.text()
    return { status: 'error', message: message || '가입 신청에 실패했습니다.' }
  }

  const data = (await response.json()) as { message: string }
  return { status: 'success', message: data.message }
}
