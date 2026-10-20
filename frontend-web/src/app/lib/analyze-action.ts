'use server'

import { cookies } from 'next/headers'

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
  etcPurpose: string | null
  familyCount: number | null
}

export type OfficialHousePrice = {
  price: number
  year: number
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
  propertyType: 'APARTMENT' | 'OFFICETEL' | 'VILLA' | 'MULTI_HOUSEHOLD'
  registry: RegistryAnalysis
  landRegistry: RegistryAnalysis | null
  marketPrice: number | null
  officialHousePrice: OfficialHousePrice | null
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
  | { status: 'payment_required'; message: string; loggedIn: boolean }

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

/**
 * 등기부 파일을 브라우저가 백엔드로 바로 올릴 때 쓸 1회용 입장권을 받는다(analyze-upload.ts).
 * 파일을 이 서버로 받아 넘기면 Server Action 요청 한도(1MB)와 배포 환경(Vercel 4.5MB)에 걸린다.
 * 로그인 쿠키는 httpOnly라 여기서만 꺼낼 수 있어 입장권 발급만 서버가 맡는다.
 */
export async function issueAnalyzeUploadTicket(): Promise<{ ticket: string } | { error: string }> {
  const token = (await cookies()).get('session')?.value
  let response: Response
  try {
    response = await fetch(`${BACKEND_URL}/api/analyze/upload-ticket`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  } catch {
    return { error: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' }
  }
  if (!response.ok) {
    return { error: (await response.text()) || '분석을 시작하지 못했습니다. 다시 시도해주세요.' }
  }
  return (await response.json()) as { ticket: string }
}
