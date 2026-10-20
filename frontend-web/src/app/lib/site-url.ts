/**
 * Route Handler에서 리다이렉트할 주소를 이 사이트의 공개 주소(OAUTH_BASE_URL) 기준으로 만든다.
 * 프록시(Caddy) 뒤에서 `next start`로 돌리면 Route Handler의 request.url이 서버 내부 주소
 * (https://localhost:3000)로 잡혀, 소셜 로그인·결제 후 localhost로 넘어가는 문제가 운영에서 실제로 있었다.
 * (proxy.ts의 리다이렉트는 공개 주소가 제대로 잡혀 해당 없음.)
 */
export function siteUrl(path: string, request: Request): URL {
  return new URL(path, process.env.OAUTH_BASE_URL ?? request.url)
}
