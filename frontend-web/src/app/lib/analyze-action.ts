'use server'

export type OwnershipEntry = {
  rank: number
  type: 'OWNERSHIP_PRESERVATION' | 'OWNERSHIP_TRANSFER' | 'OTHER'
  ownerName: string | null
  receivedDate: string | null
  cancelled: boolean
}

export type MortgageEntry = {
  rank: number
  maxClaimAmount: number
  debtorName: string | null
  mortgageeName: string | null
  receivedDate: string | null
  cancelled: boolean
}

export type SeizureEntry = {
  rank: number
  type: 'SEIZURE' | 'PROVISIONAL_SEIZURE' | 'AUCTION_COMMENCEMENT' | 'PROVISIONAL_DISPOSITION'
  receivedDate: string | null
  cancelled: boolean
}

export type RegistryAnalysis = {
  address: string | null
  uniqueNumber: string | null
  ownershipHistory: OwnershipEntry[]
  mortgages: MortgageEntry[]
  seizures: SeizureEntry[]
  totalActiveMortgageAmount: number
}

export type BuildingInfo = {
  buildingName: string | null
  mainPurpose: string | null
  structureType: string | null
  useApprovalDate: string | null
  totalFloorAreaSqm: number | null
}

export type RiskSignal = {
  code: string
  title: string
  severity: 'HIGH' | 'CAUTION' | 'INFO'
  source: 'FACTUAL' | 'LAW' | 'GOVERNMENT_GUIDELINE'
  sourceDescription: string
  detail: string
}

export type ChecklistItem = {
  title: string
  description: string
}

export type AnalyzeResult = {
  registry: RegistryAnalysis
  marketPrice: number | null
  buildingInfo: BuildingInfo | null
  riskSignals: RiskSignal[]
  hasHighRisk: boolean
  checklist: ChecklistItem[]
  disclaimer: string
}

export type AnalyzeState =
  | { status: 'idle' }
  | { status: 'error'; message: string }
  | { status: 'success'; result: AnalyzeResult }

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export async function analyzeAction(_prevState: AnalyzeState, formData: FormData): Promise<AnalyzeState> {
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

  const outgoing = new FormData()
  for (const file of files) {
    outgoing.append('files', file)
  }
  outgoing.append('propertyType', String(propertyType))
  outgoing.append('contractType', String(contractType))
  outgoing.append('depositAmount', String(depositAmount))

  for (const field of ['monthlyRent', 'buildingName', 'exclusiveAreaSqm', 'declaredLandlordName', 'declaredAddress']) {
    const value = formData.get(field)
    if (value) {
      outgoing.append(field, String(value))
    }
  }

  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/analyze`, {
      method: 'POST',
      body: outgoing,
    })
  } catch {
    return { status: 'error', message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }

  if (!response.ok) {
    const message = await response.text()
    return { status: 'error', message: message || '분석에 실패했습니다. 다시 시도해주세요.' }
  }

  const result = (await response.json()) as AnalyzeResult
  return { status: 'success', result }
}
