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

test('회원이 대화방에서 변호사를 차단하면 양쪽 입력창이 닫히고, 설정에서 해제하면 다시 대화할 수 있다', async ({
  browser,
  request,
}) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`
  const userSignup = await request.post(`${BACKEND_URL}/api/auth/signup`, {
    data: { email: `e2e-block-user-${suffix}@example.com`, password: 'password123' },
  })
  const { token: userToken } = (await userSignup.json()) as { token: string }

  // 개발용 시드 변호사(항상 승인 상태, 비밀번호를 .env로 알 수 있음)를 직접 골라 대화방을 만든다.
  // 직접 선택은 유료라서, 여기서는 무료 랜덤 문의를 연 뒤 매칭된 변호사 계정으로 로그인한다.
  const started = await request.post(`${BACKEND_URL}/api/consultations`, {
    headers: { Authorization: `Bearer ${userToken}` },
    data: { message: '차단 테스트 문의' },
  })
  const { id, lawyerId, lawyerName } = (await started.json()) as { id: number; lawyerId: number; lawyerName: string }
  const lawyers = (await (
    await request.get(`${BACKEND_URL}/api/admin/lawyers?status=APPROVED`, {
      headers: { 'X-Admin-Secret': readBackendEnv('ADMIN_SECRET') },
    })
  ).json()) as Array<{ id: number; email: string; name: string }>
  // 같은 이름의 변호사가 여럿일 수 있어 이름이 아니라 ID로 찾는다.
  const matched = lawyers.find((l) => l.id === lawyerId)!
  const isSeeded = matched.email === readBackendEnv('SEED_LAWYER_EMAIL') || /^demo-lawyer-\d+@example\.com$/.test(matched.email)
  const lawyerLogin = await request.post(`${BACKEND_URL}/api/lawyer/auth/login`, {
    data: { email: matched.email, password: isSeeded ? readBackendEnv('SEED_LAWYER_PASSWORD') : 'password123' },
  })
  const { token: lawyerToken } = (await lawyerLogin.json()) as { token: string }

  const hideNotice = () => {
    const until = String(Date.now() + 7 * 24 * 60 * 60 * 1000)
    localStorage.setItem('chat-policy-notice-hidden-until:user', until)
    localStorage.setItem('chat-policy-notice-hidden-until:lawyer', until)
  }
  const userContext = await browser.newContext()
  await userContext.addInitScript(hideNotice)
  await userContext.addCookies([{ name: 'session', value: userToken, domain: 'localhost', path: '/', httpOnly: true }])
  const lawyerContext = await browser.newContext()
  await lawyerContext.addInitScript(hideNotice)
  await lawyerContext.addCookies([{ name: 'lawyer_session', value: lawyerToken, domain: 'localhost', path: '/', httpOnly: true }])

  const userPage = await userContext.newPage()
  await userPage.goto(`/consult/${id}`)
  await userPage.getByRole('button', { name: '차단', exact: true }).click()
  const dialog = userPage.getByRole('dialog', { name: '이 변호사를 차단할까요?' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '차단하기' }).click()

  // 차단한 쪽: 입력창 대신 안내 + 차단 버튼 사라짐
  await expect(userPage.getByText('차단한 상대예요. 메시지를 주고받을 수 없어요.')).toBeVisible()
  await expect(userPage.getByPlaceholder('메시지를 입력하세요')).toHaveCount(0)
  await expect(userPage.getByRole('button', { name: '차단', exact: true })).toHaveCount(0)

  // 차단당한 쪽(변호사): 보낼 수 없다는 안내만 보인다
  const lawyerPage = await lawyerContext.newPage()
  await lawyerPage.goto(`/lawyer/consultations/${id}`)
  await expect(lawyerPage.getByText('상대방에게 메시지를 보낼 수 없어요.')).toBeVisible()
  await expect(lawyerPage.getByPlaceholder('답변을 입력하세요')).toHaveCount(0)
  // 화면을 우회해 API로 보내도 서버가 막는다
  const forced = await request.post(`${BACKEND_URL}/api/lawyer/consultations/${id}/messages`, {
    headers: { Authorization: `Bearer ${lawyerToken}` },
    data: { content: '우회 메시지' },
  })
  expect(forced.status()).toBe(400)

  // 설정 > 차단 관리에서 해제
  await userPage.getByRole('link', { name: '차단 관리' }).click()
  await expect(userPage).toHaveURL(/\/settings$/)
  const row = userPage.locator('li', { hasText: `${lawyerName} 변호사` })
  await expect(row).toBeVisible()
  userPage.once('dialog', (d) => d.accept())
  await row.getByRole('button', { name: '차단 해제' }).click()
  await expect(userPage.getByText('차단한 변호사가 없어요.')).toBeVisible()

  await userPage.goto(`/consult/${id}`)
  await expect(userPage.getByPlaceholder('메시지를 입력하세요')).toBeVisible()

  await userContext.close()
  await lawyerContext.close()
})
