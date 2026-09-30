import { expect, test, type Page } from '@playwright/test'
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

async function loginAsAdmin(page: Page) {
  // 관리자도 일반 로그인 화면에서 로그인한다 — 로그인하면 /admin으로 이동한다.
  await page.goto('/login')
  await page.getByLabel('이메일').fill(readBackendEnv('ADMIN_EMAIL'))
  await page.getByLabel('비밀번호').fill(readBackendEnv('ADMIN_PASSWORD'))
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
}

// 문의는 승인된 변호사 중 무작위로 매칭되는데, 그 후보에는 서버 시작 시 만들어지는 개발용 시드 변호사
// (backend/.env의 SEED_LAWYER_*)와 가상 변호사(demo-lawyer-NN@example.com)도 포함된다. 이들은 비밀번호가
// SEED_LAWYER_PASSWORD라서 여기서 구분한다.
function lawyerPasswordFor(email: string): string {
  const isSeeded = email === readBackendEnv('SEED_LAWYER_EMAIL') || /^demo-lawyer-\d+@example\.com$/.test(email)
  return isSeeded ? readBackendEnv('SEED_LAWYER_PASSWORD') : 'password123'
}

function uniqueSuffix() {
  return `${Date.now()}-${Math.floor(Math.random() * 100000)}`
}

test.describe('상담 신고 및 관리자 정지 처리', () => {
  test('로그인하지 않으면 관리자 페이지 대신 로그인 화면으로 이동한다', async ({ page }) => {
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/login\?redirect=%2Fadmin/)
  })

  test('관리자 계정으로 일반 로그인 화면에서 로그인하면 관리자 페이지로 이동한다', async ({ page }) => {
    await loginAsAdmin(page)
    await expect(page.getByRole('heading', { name: '관리자 페이지' })).toBeVisible()
  })

  test('대화방에 들어오면 이용 안내가 뜨고, 일주일 동안 안 보기를 누르면 다시 뜨지 않는다', async ({ page, request }) => {
    const suffix = uniqueSuffix()
    const userSignup = await request.post(`${BACKEND_URL}/api/auth/signup`, {
      data: { email: `e2e-notice-user-${suffix}@example.com`, password: 'password123' },
    })
    const { token } = (await userSignup.json()) as { token: string }
    // 개발용 시드 변호사가 항상 승인 상태로 있으므로 문의는 반드시 매칭된다.
    const started = await request.post(`${BACKEND_URL}/api/consultations`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { message: '이용 안내 확인용 문의' },
    })
    const { id } = (await started.json()) as { id: number }
    await page.context().addCookies([{ name: 'session', value: token, domain: 'localhost', path: '/', httpOnly: true }])

    await page.goto(`/consult/${id}`)
    const notice = page.getByRole('dialog', { name: '상담 이용 안내' })
    await expect(notice).toBeVisible()
    await expect(notice.getByText('욕설·모욕')).toBeVisible()
    await expect(notice.getByText('금전 요구')).toBeVisible()

    // "확인"은 이번에만 닫는다 — 다시 들어오면 또 뜬다.
    await notice.getByRole('button', { name: '확인' }).click()
    await expect(notice).toBeHidden()
    await page.reload()
    await expect(notice).toBeVisible()

    // "일주일 동안 안 보기"는 7일 뒤까지 저장되어 다시 들어와도 뜨지 않는다.
    await notice.getByRole('button', { name: '일주일 동안 안 보기' }).click()
    await expect(notice).toBeHidden()
    const hiddenUntil = Number(await page.evaluate(() => localStorage.getItem('chat-policy-notice-hidden-until:user')))
    const days = (hiddenUntil - Date.now()) / (24 * 60 * 60 * 1000)
    expect(days).toBeGreaterThan(6.9)
    expect(days).toBeLessThanOrEqual(7)
    await page.reload()
    await expect(page.getByText('이용 안내 확인용 문의')).toBeVisible()
    await expect(notice).toBeHidden()
  })

  test('회원이 금전 요구로 변호사를 신고하면 관리자가 대화를 확인하고 정지할 수 있다', async ({ page, request }) => {
    // 대화방 이용 안내 팝업은 별도 테스트(moderation.spec.ts)에서 검증하므로 여기서는 미리 숨겨둔다.
    await page.addInitScript(() => {
      const until = String(Date.now() + 7 * 24 * 60 * 60 * 1000)
      localStorage.setItem('chat-policy-notice-hidden-until:user', until)
      localStorage.setItem('chat-policy-notice-hidden-until:lawyer', until)
    })
    const adminSecret = readBackendEnv('ADMIN_SECRET')
    const admin = { 'X-Admin-Secret': adminSecret }
    const suffix = uniqueSuffix()

    // 승인된 변호사 1명 준비 (다른 스펙이 만든 승인 변호사가 매칭될 수도 있어서, 실제 매칭 결과는 아래에서 확인)
    const lawyerEmail = `e2e-mod-lawyer-${suffix}@example.com`
    await request.post(`${BACKEND_URL}/api/lawyer/auth/signup`, {
      multipart: {
        email: lawyerEmail,
        password: 'password123',
        name: `E2E신고변호사-${suffix}`,
        barNumber: '00000',
        documents: { name: 'license.pdf', mimeType: 'application/pdf', buffer: Buffer.from('dummy') },
      },
    })
    const pending = (await (await request.get(`${BACKEND_URL}/api/admin/lawyers?status=PENDING`, { headers: admin })).json()) as Array<{ id: number; email: string }>
    const created = pending.find((l) => l.email === lawyerEmail)!
    await request.post(`${BACKEND_URL}/api/admin/lawyers/${created.id}/approve`, { headers: admin })

    const userSignup = await request.post(`${BACKEND_URL}/api/auth/signup`, {
      data: { email: `e2e-mod-user-${suffix}@example.com`, password: 'password123' },
    })
    const { token: userToken } = (await userSignup.json()) as { token: string }
    const userAuth = { Authorization: `Bearer ${userToken}` }

    const started = await request.post(`${BACKEND_URL}/api/consultations`, {
      headers: userAuth,
      data: { message: '전세 보증금 반환 문의드립니다' },
    })
    const { id: consultationId, lawyerName } = (await started.json()) as { id: number; lawyerName: string }

    // 회원이 대화방에서 신고 — 기준(욕설·모욕/금전 요구) 중 하나를 골라야만 접수 버튼이 활성화된다
    await page.context().addCookies([{ name: 'session', value: userToken, domain: 'localhost', path: '/', httpOnly: true }])
    await page.goto(`/consult/${consultationId}`)
    await page.getByRole('button', { name: '신고', exact: true }).click()
    await expect(page.getByRole('heading', { name: '변호사 신고하기' })).toBeVisible()
    await expect(page.getByRole('button', { name: '신고하기' })).toBeDisabled()
    await page.getByText('금전 요구', { exact: true }).click()
    const detail = `개인 계좌로 착수금 입금 요구 ${suffix}`
    await page.getByPlaceholder('상황을 간단히 적어주세요 (선택)').fill(detail)
    await page.getByRole('button', { name: '신고하기' }).click()
    await expect(page.getByRole('heading', { name: '신고가 접수됐어요' })).toBeVisible()
    await page.getByRole('button', { name: '확인' }).click()

    // 관리자 페이지에서 신고 확인 → 대화 원문 확인 → 정지
    await loginAsAdmin(page)
    await page.getByRole('button', { name: '신고 관리' }).click()

    const card = page.locator('li', { hasText: detail })
    await expect(card).toBeVisible()
    await expect(card.getByText('금전 요구', { exact: true })).toBeVisible()
    await card.getByRole('button', { name: '대화 원문 보기' }).click()
    await expect(card.getByText('전세 보증금 반환 문의드립니다')).toBeVisible()

    page.once('dialog', (dialog) => dialog.accept())
    await card.getByRole('button', { name: '기준 충족 · 이용 정지' }).click()
    await expect(page.locator('li', { hasText: detail })).toHaveCount(0)

    // 정지된 변호사는 로그인할 수 없고, 회원 쪽 대화방은 입력창 대신 안내 문구를 보여준다
    const approved = (await (await request.get(`${BACKEND_URL}/api/admin/members/lawyers`, { headers: admin })).json()) as Array<{ id: number; email: string; name: string; blocked: boolean }>
    const matched = approved.find((l) => l.name === lawyerName)!
    expect(matched.blocked).toBe(true)
    const login = await request.post(`${BACKEND_URL}/api/lawyer/auth/login`, {
      data: { email: matched.email, password: lawyerPasswordFor(matched.email) },
    })
    expect(login.status()).toBe(403)
    expect(await login.text()).toContain('이용이 정지된 계정')

    await page.goto(`/consult/${consultationId}`)
    await expect(page.getByText('상대방이 이용 정지되어 더 이상 대화할 수 없어요.')).toBeVisible()

    // 회원 관리 탭에서 정지 해제 (다른 스펙의 무작위 매칭에 영향을 주지 않도록 원상복구)
    await loginAsAdmin(page)
    await page.getByRole('button', { name: '회원 관리' }).click()
    await page.getByRole('button', { name: '변호사', exact: true }).click()
    await page.getByPlaceholder('이메일·이름 검색').fill(matched.email)
    const row = page.locator('li', { hasText: matched.email })
    await expect(row.getByText('정지됨')).toBeVisible()
    page.once('dialog', (dialog) => dialog.accept())
    await row.getByRole('button', { name: '정지 해제' }).click()
    await expect(row.getByRole('button', { name: '이용 정지' })).toBeVisible()
  })
})
