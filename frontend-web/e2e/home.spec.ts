import { expect, test } from '@playwright/test'

test.describe('메인 페이지', () => {
  test('핵심 섹션이 모두 보인다', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveTitle('Project Guard')
    await expect(page.getByRole('heading', { name: /등기부등본부터 확인하세요/ })).toBeVisible()
    await expect(page.getByText('40,936명')).toBeVisible()
    await expect(page.getByRole('heading', { name: '이런 집은 특히 조심하세요' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '감이 아니라, 근거로 판단해요' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '이용 방법은 간단해요' })).toBeVisible()
    await expect(page.getByText('본인이 계약 당사자이거나 본인 명의로 열람 가능한 부동산에 한해 사용해주세요.')).toBeVisible()
  })

  test('무료로 확인하기 버튼을 누르면 분석 폼으로 이동한다', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: '무료로 확인하기' }).click()
    await expect(page.locator('#analyze')).toBeInViewport()
    await expect(page.getByRole('button', { name: '분석하기' })).toBeVisible()
  })

  test('로그인 전엔 변호사 무료 상담 버튼을 눌러도 로그인 화면으로 이동한다', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: '변호사와 무료로 상담하기 💬' }).click()

    await expect(page).toHaveURL(/\/login\?redirect=/)
  })
})
