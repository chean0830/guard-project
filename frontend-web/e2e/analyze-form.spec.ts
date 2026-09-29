import { expect, test } from '@playwright/test'

test.describe('분석 폼', () => {
  test('필수/선택 뱃지가 구분되어 보인다', async ({ page }) => {
    await page.goto('/#analyze')

    const requiredBadges = page.getByText('필수', { exact: true })
    const optionalBadges = page.getByText('선택', { exact: true })

    await expect(requiredBadges).toHaveCount(4) // 등기부등본, 부동산 유형, 계약 형태, 보증금
    await expect(optionalBadges).toHaveCount(4) // 단지/건물명, 전용면적, 임대인 이름, 계약서상 주소
  })

  test('월세를 선택하면 월세 입력란이 나타난다', async ({ page }) => {
    await page.goto('/#analyze')

    await expect(page.getByLabel('월세 (원)')).not.toBeVisible()
    await page.getByLabel('계약 형태').selectOption('WOLSE')
    await expect(page.getByLabel('월세 (원)')).toBeVisible()
  })

  test('파일을 추가하고 삭제할 수 있다', async ({ page }) => {
    await page.goto('/#analyze')

    await page.locator('input[type="file"]:not([capture])').setInputFiles({
      name: 'test1.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 dummy content'),
    })

    await expect(page.getByText('1. test1.pdf')).toBeVisible()

    await page.getByRole('button', { name: '삭제' }).click()
    await expect(page.getByText('1. test1.pdf')).not.toBeVisible()
  })

  test('보증금을 비워두면 브라우저 기본 검증에 걸려 제출되지 않는다', async ({ page }) => {
    await page.goto('/#analyze')

    await page.getByRole('button', { name: '분석하기' }).click()

    const isValid = await page.locator('#depositAmount').evaluate((el: HTMLInputElement) => el.validity.valid)
    expect(isValid).toBe(false)
    // 실제 제출(에러 메시지 등장)로 이어지지 않았어야 한다.
    await expect(page.getByText('등기부등본 파일을 1장 이상 선택해주세요.')).not.toBeVisible()
  })
})
