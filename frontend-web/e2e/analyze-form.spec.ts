import { expect, test } from '@playwright/test'

test.describe('분석 폼', () => {
  test('필수/선택 뱃지가 구분되어 보인다', async ({ page }) => {
    await page.goto('/#analyze')

    const requiredBadges = page.getByText('필수', { exact: true })
    const optionalBadges = page.getByText('선택', { exact: true })

    await expect(requiredBadges).toHaveCount(4) // 등기부등본, 부동산 유형, 계약 형태, 보증금
    await expect(optionalBadges).toHaveCount(5) // 단지/건물명, 전용면적, 임대인 이름, 계약서상 주소, 위반건축물 표시
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

  test('원룸·다가구주택을 고르면 경고 문구와 선순위 보증금 입력란이 나타난다', async ({ page }) => {
    await page.goto('/#analyze')

    await expect(page.getByText('다가구주택은 등기부만으로 안전한지 알 수 없어요')).not.toBeVisible()
    await page.getByLabel('부동산 유형').selectOption('MULTI_HOUSEHOLD')

    await expect(page.getByText('다가구주택은 등기부만으로 안전한지 알 수 없어요')).toBeVisible()
    await expect(page.getByLabel('먼저 들어온 세입자 보증금 합계 (원)')).toBeVisible()
    await expect(page.getByLabel('건물 전체 시세 (원)')).toBeVisible()
    await expect(page.getByRole('button', { name: '토지 등기부 선택' })).toBeVisible()
    // 다가구는 시세를 자동 조회하지 않아 단지명·전용면적 입력란을 숨긴다.
    await expect(page.getByLabel('단지/건물명')).not.toBeVisible()

    // 모른다고 고르면 금액 입력란은 필요 없다.
    await page.getByLabel('먼저 들어온 세입자 보증금, 어떻게 확인하셨나요?').selectOption('UNKNOWN')
    await expect(page.getByLabel('먼저 들어온 세입자 보증금 합계 (원)')).not.toBeVisible()
  })
})
