import { expect, test, type APIRequestContext } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const BACKEND_URL = 'http://localhost:8080'

function uniqueEmail() {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`
}

function readBackendEnv(key: string): string {
  const content = fs.readFileSync(path.resolve(__dirname, '../../backend/.env'), 'utf-8')
  const match = content.match(new RegExp(`^${key}=(.*)$`, 'm'))
  if (!match) throw new Error(`backend/.env에서 ${key}를 찾을 수 없습니다.`)
  return match[1].trim()
}

/** 이메일 인증 메일을 받을 수 없는 테스트 환경이라, 내부 비밀값으로 인증을 건너뛰어 회원을 만든다. */
async function createUser(request: APIRequestContext, email: string) {
  const response = await request.post(`${BACKEND_URL}/api/auth/signup`, {
    headers: { 'X-Internal-Secret': readBackendEnv('INTERNAL_SYNC_SECRET') },
    data: { email, password: 'password123' },
  })
  expect(response.ok()).toBeTruthy()
}

test.describe('로그인/회원가입', () => {
  test('로그인하지 않고 상담 페이지에 들어가면 로그인 화면으로 이동한다', async ({ page }) => {
    await page.goto('/consult')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })

  test('이메일 인증을 마치기 전에는 가입할 수 없고, 틀린 인증번호는 거부된다', async ({ page }) => {
    await page.goto('/signup')
    await expect(page.getByLabel('비밀번호')).toBeDisabled()
    await expect(page.getByRole('button', { name: '회원가입' })).toBeDisabled()

    await page.getByLabel('이메일').fill(uniqueEmail())
    await page.getByRole('button', { name: '인증번호 받기' }).click()
    await expect(page.getByText('인증번호를 보냈어요.', { exact: false })).toBeVisible()

    await page.getByLabel('인증번호').fill('000000')
    await page.getByRole('button', { name: '확인' }).click()
    await expect(page.getByText('인증번호가 맞지 않아요.', { exact: false })).toBeVisible()
    await expect(page.getByRole('button', { name: '회원가입' })).toBeDisabled()
  })

  test('이미 가입된 이메일로는 인증번호를 받을 수 없다', async ({ page, request }) => {
    const email = uniqueEmail()
    await createUser(request, email)

    await page.goto('/signup')
    await page.getByLabel('이메일').fill(email)
    await page.getByRole('button', { name: '인증번호 받기' }).click()
    await expect(page.getByText('이미 가입된 이메일입니다.', { exact: false })).toBeVisible()
  })

  test('인증을 건너뛴 가입 요청은 서버가 거부한다', async ({ request }) => {
    const response = await request.post(`${BACKEND_URL}/api/auth/signup`, {
      data: { email: uniqueEmail(), password: 'password123' },
    })
    expect(response.status()).toBe(400)
    expect(await response.text()).toContain('이메일 인증')
  })

  test('로그인하면 로그인 상태가 되고, 로그아웃하면 풀린다', async ({ page, request }) => {
    const email = uniqueEmail()
    await createUser(request, email)

    await page.goto('/login')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('password123')
    await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page).toHaveURL('/')
    await expect(page.getByText(`${email}님`)).toBeVisible()

    await page.goto('/consult')
    await expect(page).toHaveURL('/consult')

    await page.getByRole('button', { name: '로그아웃' }).click()
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('link', { name: '로그인' })).toBeVisible()

    await page.goto('/consult')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })

  test('틀린 비밀번호로 로그인하면 에러가 보인다', async ({ page, request }) => {
    const email = uniqueEmail()
    await createUser(request, email)

    await page.goto('/login')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('wrongpassword')
    await page.getByRole('button', { name: '로그인', exact: true }).click()

    await expect(page.getByText('이메일 또는 비밀번호가 올바르지 않습니다.', { exact: false })).toBeVisible()
  })
})
