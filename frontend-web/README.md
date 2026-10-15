# frontend-web (Next.js)

Project Guard 웹 화면 (Next.js 16 App Router · TypeScript · Tailwind).

## 실행 방법

```bash
cp .env.example .env.local   # 각 항목 설명은 .env.example 주석 참고
npm install
npm run dev                  # http://localhost:3000 (백엔드가 http://localhost:8080 에 떠 있어야 한다)
```

## 검사

```bash
npm run lint
npx tsc --noEmit
npm run build
npm run test:e2e   # Playwright. 백엔드와 웹 서버가 떠 있어야 한다
```

## 구조

- `src/app/**/page.tsx` — 화면 (회원 `/`, `/consult`, `/settings` · 변호사 `/lawyer` · 관리자 `/admin`)
- `src/app/ui` — 화면 컴포넌트
- `src/app/lib` — Server Action. 브라우저는 백엔드를 직접 부르지 않고 여기를 거친다 (세션은 httpOnly 쿠키)
- `src/app/api` — 소셜 로그인 콜백, 결제 성공 처리, 웹 푸시 구독
- `src/proxy.ts` — 로그인이 필요한 경로 가드
- `e2e` — Playwright 테스트
