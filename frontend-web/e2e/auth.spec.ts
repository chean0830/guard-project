import { expect, test } from '@playwright/test'

function uniqueEmail() {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`
}

test.describe('로그인/회원가입', () => {
  test('로그인하지 않고 상담 페이지에 들어가면 로그인 화면으로 이동한다', async ({ page }) => {
    await page.goto('/consult')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })

  test('회원가입하면 로그인 상태가 되고, 로그아웃하면 풀린다', async ({ page }) => {
    const email = uniqueEmail()

    await page.goto('/signup')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('password123')
    await page.getByRole('button', { name: '회원가입' }).click()

    await expect(page).toHaveURL('/')
    await expect(page.getByText(`${email}님`)).toBeVisible()

    // 로그인 상태면 상담 페이지에 바로 들어갈 수 있다.
    await page.goto('/consult')
    await expect(page).toHaveURL('/consult')

    await page.goto('/')
    await page.getByRole('button', { name: '로그아웃' }).click()
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('link', { name: '로그인' })).toBeVisible()

    // 로그아웃 후엔 다시 상담 페이지 접근이 막힌다.
    await page.goto('/consult')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })

  test('같은 이메일로 두 번 가입하면 에러가 보인다', async ({ page }) => {
    const email = uniqueEmail()

    await page.goto('/signup')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('password123')
    await page.getByRole('button', { name: '회원가입' }).click()
    await expect(page).toHaveURL('/')

    // 로그아웃 후 같은 이메일로 재가입 시도
    await page.getByRole('button', { name: '로그아웃' }).click()
    await page.goto('/signup')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('password123')
    await page.getByRole('button', { name: '회원가입' }).click()

    await expect(page.getByText('이미 가입된 이메일입니다.')).toBeVisible()
  })

  test('틀린 비밀번호로 로그인하면 에러가 보인다', async ({ page }) => {
    const email = uniqueEmail()

    await page.goto('/signup')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('password123')
    await page.getByRole('button', { name: '회원가입' }).click()
    await expect(page).toHaveURL('/')
    await page.getByRole('button', { name: '로그아웃' }).click()

    await page.goto('/login')
    await page.getByLabel('이메일').fill(email)
    await page.getByLabel('비밀번호').fill('wrongpassword')
    await page.getByRole('button', { name: '로그인' }).click()

    await expect(page.getByText('이메일 또는 비밀번호가 올바르지 않습니다.')).toBeVisible()
  })
})
