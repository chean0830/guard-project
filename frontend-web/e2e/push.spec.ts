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

test('문의를 등록하면 접수 팝업이 뜨고, 대화 보기로 스레드로 이동한다', async ({ page, context }) => {
  await context.grantPermissions(['notifications'])

  const userEmail = uniqueEmail('e2e-push-user')
  const signupResponse = await page.request.post(`${BACKEND_URL}/api/auth/signup`, {
    data: { email: userEmail, password: 'password123' },
  })
  const { token } = (await signupResponse.json()) as { token: string }

  // 승인된 변호사가 최소 1명은 있어야 문의가 성립하므로 하나 만들어둔다 (다른 e2e 스펙이
  // 이미 여럿 만들어뒀을 수도 있지만, 이 스펙만 단독 실행해도 통과하도록 직접 보장한다).
  const adminSecret = readBackendEnv('ADMIN_SECRET')

  const lawyerEmail = uniqueEmail('e2e-push-lawyer')
  await page.request.post(`${BACKEND_URL}/api/lawyer/auth/signup`, {
    multipart: {
      email: lawyerEmail,
      password: 'password123',
      name: 'E2E푸시변호사',
      barNumber: '00001',
      documents: { name: 'license.pdf', mimeType: 'application/pdf', buffer: Buffer.from('dummy') },
    },
  })
  const pending = (await (
    await page.request.get(`${BACKEND_URL}/api/admin/lawyers?status=PENDING`, {
      headers: { 'X-Admin-Secret': adminSecret },
    })
  ).json()) as Array<{ id: number; email: string }>
  const lawyer = pending.find((l) => l.email === lawyerEmail)!
  await page.request.post(`${BACKEND_URL}/api/admin/lawyers/${lawyer.id}/approve`, {
    headers: { 'X-Admin-Secret': adminSecret },
  })

  await context.addCookies([{ name: 'session', value: token, domain: 'localhost', path: '/', httpOnly: true }])

  await page.goto('/consult')
  await page.getByPlaceholder(/등기부에 근저당이 있는데/).fill('보증금 관련 문의입니다')
  await page.getByRole('button', { name: '문의 보내기' }).click()

  await expect(page.getByText('문의가 접수됐어요')).toBeVisible()
  await expect(page.getByText('변호사님이 답변을 준비중이에요.')).toBeVisible()
  await expect(page.getByRole('button', { name: '답변 오면 알림 받기' })).toBeVisible()

  // 실제 웹 푸시 구독은 헤드리스 브라우저가 구글 FCM 서버까지 도달해야 완료되는데,
  // 이 테스트 환경에서는 그 외부 연결 자체가 되지 않아(AbortError) 끝까지 검증할 수 없다 —
  // 백엔드 쪽 발송 로직과 실패 시에도 상담 흐름이 깨지지 않는지는 별도로 라이브 검증했다
  // (docs/결정사항.md 참고). 여기서는 팝업이 뜨고 "대화 보기"로 정상 이동하는지만 확인한다.
  await page.getByRole('button', { name: '대화 보기' }).click()
  await expect(page).toHaveURL(/\/consult\/\d+/)
  await expect(page.getByText('보증금 관련 문의입니다')).toBeVisible()
})
