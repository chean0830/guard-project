import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  // 문의는 승인된 변호사 중 무작위로 매칭되고, 신고 테스트는 매칭된 변호사를 잠시 정지시킨다. 병렬로 돌리면
  // 다른 테스트의 상대 변호사가 그 사이 정지돼 결과가 흔들리므로(실제로 차단 테스트가 간헐 실패함) 한 번에 하나씩 돌린다.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
