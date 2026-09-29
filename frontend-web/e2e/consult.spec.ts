import { expect, test } from '@playwright/test'

test.describe('변호사 상담 데모 채팅', () => {
  test('매칭된 변호사 정보와 인사말이 보인다', async ({ page }) => {
    await page.goto('/consult/kim-doyoon')

    await expect(page.getByText('김도윤 변호사', { exact: true })).toBeVisible()
    await expect(page.getByText('법률사무소 이음')).toBeVisible()
    await expect(page.getByText('전세사기 피해자 소송 다수 승소')).toBeVisible()
    await expect(page.getByText(/안녕하세요, 김도윤 변호사입니다/)).toBeVisible()
    await expect(page.getByText('포트폴리오 데모이며 실제 변호사 상담이 아닙니다')).toBeVisible()
  })

  test('메시지를 보내면 정해진 문구로 답이 온다', async ({ page }) => {
    await page.goto('/consult/kim-doyoon')

    await page.getByPlaceholder('메시지를 입력하세요').fill('보증금을 못 받고 있어요')
    await page.getByRole('button', { name: '보내기' }).click()

    await expect(page.getByText('보증금을 못 받고 있어요')).toBeVisible()
    await expect(page.getByText('등기부등본과 계약서를 함께 확인해야 해요')).toBeVisible({ timeout: 5000 })
  })

  test('존재하지 않는 변호사는 404를 반환한다', async ({ page }) => {
    const response = await page.goto('/consult/no-such-lawyer')
    expect(response?.status()).toBe(404)
  })
})
