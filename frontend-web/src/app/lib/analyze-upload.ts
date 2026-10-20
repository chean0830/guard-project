import { issueAnalyzeUploadTicket, type AnalyzeResult, type AnalyzeState } from '@/app/lib/analyze-action'

// 브라우저가 직접 부르는 백엔드 주소. 파일 업로드만 Next.js 서버를 거치지 않는다(analyze-action.ts 참고).
export const PUBLIC_BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL ?? 'http://localhost:8080'

// 백엔드 spring.servlet.multipart 설정과 같은 값. 넘으면 백엔드가 응답 전에 끊어 이유를 알 수 없으므로 먼저 막는다.
const MAX_FILE_BYTES = 20 * 1024 * 1024
const MAX_REQUEST_BYTES = 100 * 1024 * 1024

export const UPLOAD_FAILED_MESSAGE =
  '파일을 올리지 못했어요. 인터넷 연결을 확인하고, 파일은 장당 20MB 이하인지 확인해주세요.'

/** 파일 용량이 백엔드 한도 안이면 null, 넘으면 안내 문구. */
export function fileSizeProblem(files: File[]): string | null {
  if (files.some((file) => file.size > MAX_FILE_BYTES)) {
    return '파일 하나는 20MB까지 올릴 수 있어요. 사진 해상도를 낮추거나 PDF로 올려주세요.'
  }
  if (files.reduce((sum, file) => sum + file.size, 0) > MAX_REQUEST_BYTES) {
    return '한 번에 올리는 파일은 모두 합쳐 100MB까지예요.'
  }
  return null
}

const OPTIONAL_FIELDS = [
  'monthlyRent',
  'buildingName',
  'exclusiveAreaSqm',
  'declaredLandlordName',
  'declaredAddress',
  'priorDepositTotal',
  'priorDepositSource',
  'buildingPrice',
  'violationBuilding',
  'roomCount',
]

/** 분석 폼 제출. 입장권을 받은 뒤 파일을 백엔드로 바로 올린다. */
export async function submitAnalysis(_prevState: AnalyzeState, formData: FormData): Promise<AnalyzeState> {
  const files = formData.getAll('files').filter((f): f is File => f instanceof File && f.size > 0)
  if (files.length === 0) {
    return { status: 'error', message: '등기부등본 파일을 1장 이상 선택해주세요.' }
  }

  const propertyType = formData.get('propertyType')
  const contractType = formData.get('contractType')
  const depositAmount = formData.get('depositAmount')
  if (!propertyType || !contractType || !depositAmount) {
    return { status: 'error', message: '부동산 유형, 계약 형태, 보증금을 입력해주세요.' }
  }

  const landFiles = formData.getAll('landFiles').filter((f): f is File => f instanceof File && f.size > 0)
  const sizeProblem = fileSizeProblem([...files, ...landFiles])
  if (sizeProblem) {
    return { status: 'error', message: sizeProblem }
  }

  const outgoing = new FormData()
  for (const file of files) {
    outgoing.append('files', file)
  }
  outgoing.append('propertyType', String(propertyType))
  outgoing.append('contractType', String(contractType))
  outgoing.append('depositAmount', String(depositAmount))
  for (const file of landFiles) {
    outgoing.append('landFiles', file)
  }
  for (const field of OPTIONAL_FIELDS) {
    const value = formData.get(field)
    if (value) {
      outgoing.append(field, String(value))
    }
  }

  const issued = await issueAnalyzeUploadTicket()
  if ('error' in issued) {
    return { status: 'error', message: issued.error }
  }

  let response: Response
  try {
    response = await fetch(`${PUBLIC_BACKEND_URL}/api/analyze`, {
      method: 'POST',
      headers: { 'X-Upload-Ticket': issued.ticket },
      body: outgoing,
    })
  } catch {
    return { status: 'error', message: UPLOAD_FAILED_MESSAGE }
  }

  if (response.status === 402) {
    return { status: 'payment_required', message: await response.text(), loggedIn: true }
  }

  if (!response.ok) {
    const message = await response.text()
    return { status: 'error', message: message || '분석에 실패했습니다. 다시 시도해주세요.' }
  }

  const result = (await response.json()) as AnalyzeResult
  return { status: 'success', result }
}
