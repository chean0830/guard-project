import { expect, test } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BACKEND_URL = 'http://localhost:8080'

function readBackendEnv(key: string): string {
  const envPath = path.resolve(__dirname, '../../backend/.env')
  const content = fs.readFileSync(envPath, 'utf-8')
  const match = content.match(new RegExp(`^${key}=(.*)$`, 'm'))
  if (!match) throw new Error(`backend/.env에서 ${key}를 찾을 수 없습니다.`)
  return match[1].trim()
}

function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`
}

test.describe('실제 변호사 상담 문의', () => {
  test('로그인하지 않으면 상담 페이지에 접근할 수 없다', async ({ page }) => {
    await page.goto('/consult')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })

  test('회원이 문의를 남기고 변호사가 답장하면 서로 볼 수 있다', async ({ page, request }) => {
    const adminSecret = readBackendEnv('ADMIN_SECRET')

    // 승인된 변호사 계정 준비: 가입 -> 관리자 승인. 인메모리 DB에는 다른 e2e 스펙이 만들어둔
    // 승인된 변호사도 이미 여럿 있을 수 있어(문의는 그중 무작위로 매칭됨), 실제로 어떤
    // 변호사가 매칭되는지는 화면에 표시된 이름으로 나중에 알아낸다 — 그래서 이름을 이메일처럼
    // 유일하게 만들어 다른 변호사와 절대 헷갈리지 않게 한다.
    const uniqueSuffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`
    const lawyerName = `E2E변호사-${uniqueSuffix}`
    const lawyerEmail = uniqueEmail('e2e-lawyer')
    const signupResponse = await request.post(`${BACKEND_URL}/api/lawyer/auth/signup`, {
      multipart: {
        email: lawyerEmail,
        password: 'password123',
        name: lawyerName,
        barNumber: '00000',
        documents: {
          name: 'license.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('dummy license'),
        },
      },
    })
    expect(signupResponse.ok()).toBeTruthy()

    const listResponse = await request.get(`${BACKEND_URL}/api/admin/lawyers?status=PENDING`, {
      headers: { 'X-Admin-Secret': adminSecret },
    })
    const pending = (await listResponse.json()) as Array<{ id: number; email: string }>
    const lawyer = pending.find((l) => l.email === lawyerEmail)
    expect(lawyer).toBeTruthy()

    const approveResponse = await request.post(`${BACKEND_URL}/api/admin/lawyers/${lawyer!.id}/approve`, {
      headers: { 'X-Admin-Secret': adminSecret },
    })
    expect(approveResponse.ok()).toBeTruthy()

    // 회원 계정 준비
    const userEmail = uniqueEmail('e2e-user')
    const userSignupResponse = await request.post(`${BACKEND_URL}/api/auth/signup`, {
      data: { email: userEmail, password: 'password123' },
    })
    const { token: userToken } = (await userSignupResponse.json()) as { token: string }

    await page.context().addCookies([
      { name: 'session', value: userToken, domain: 'localhost', path: '/', httpOnly: true },
    ])

    // 회원이 문의를 남긴다 — 접수 팝업이 뜨고, "대화 보기"를 눌러야 실제 스레드로 이동한다.
    // 승인된 변호사가 여럿이라 무작위 매칭 결과가 이 테스트가 만든 변호사가 아닐 수도 있다.
    await page.goto('/consult')
    await page.getByPlaceholder(/등기부에 근저당이 있는데/).fill('보증금을 못 받고 있어요')
    await page.getByRole('button', { name: '문의 보내기' }).click()

    await expect(page.getByText('문의가 접수됐어요')).toBeVisible()
    await page.getByRole('button', { name: '대화 보기' }).click()

    await expect(page).toHaveURL(/\/consult\/\d+/)
    await expect(page.getByText('보증금을 못 받고 있어요')).toBeVisible()
    const consultationId = page.url().split('/').pop()

    // 실제로 매칭된 변호사가 누구인지 화면에 표시된 이름으로 확인하고, 그 변호사로 로그인한다
    // (이 테스트가 만든 변호사가 아니라 다른 e2e 스펙이 만든 변호사가 매칭됐을 수도 있으므로,
    // "우리가 만든 변호사"라고 가정하지 않는다 — 같은 셋업으로 만든 변호사는 전부 비밀번호가
    // password123이라 이메일만 알면 로그인할 수 있다).
    const matchedLawyerName = await page.locator('p.font-semibold').first().textContent()
    const approvedResponse = await request.get(`${BACKEND_URL}/api/admin/lawyers?status=APPROVED`, {
      headers: { 'X-Admin-Secret': adminSecret },
    })
    const approved = (await approvedResponse.json()) as Array<{ email: string; name: string }>
    const matchedLawyer = approved.find((l) => l.name === matchedLawyerName?.trim())
    expect(matchedLawyer).toBeTruthy()

    const lawyerLoginResponse = await request.post(`${BACKEND_URL}/api/lawyer/auth/login`, {
      data: { email: matchedLawyer!.email, password: 'password123' },
    })
    const { token: lawyerToken } = (await lawyerLoginResponse.json()) as { token: string }
    await page.context().addCookies([
      { name: 'lawyer_session', value: lawyerToken, domain: 'localhost', path: '/', httpOnly: true },
    ])

    // 변호사 쪽 문의함에서 보이고, 답장할 수 있다
    await page.goto(`/lawyer/consultations/${consultationId}`)
    await expect(page.getByText('보증금을 못 받고 있어요')).toBeVisible()
    await page.getByPlaceholder('답변을 입력하세요').fill('상황을 더 자세히 말씀해주세요')
    await page.getByRole('button', { name: '보내기' }).click()
    await expect(page.getByText('상황을 더 자세히 말씀해주세요')).toBeVisible()

    // 회원 쪽에서 변호사 답장이 (폴링으로) 보인다
    await page.goto(`/consult/${consultationId}`)
    await expect(page.getByText('상황을 더 자세히 말씀해주세요')).toBeVisible({ timeout: 8000 })
  })
})
