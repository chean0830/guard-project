import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { getProvider } from '@/app/lib/oauth-providers'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'
const INTERNAL_SYNC_SECRET = process.env.INTERNAL_SYNC_SECRET ?? ''

/**
 * 구글/카카오/네이버가 로그인 후 돌아오는 주소. 여기서만 code를 실제 토큰으로 교환하고
 * 프로필을 조회한다 — 브라우저는 이 서버 라우트를 거칠 뿐, provider의 토큰/키를 직접 보지 않는다.
 * 검증이 끝나면 백엔드 /api/auth/oauth-sync를 호출해 우리 서비스 세션 토큰을 받아 쿠키에 심는다.
 */
export async function GET(request: Request, { params }: { params: Promise<{ provider: string }> }) {
  const { provider: providerName } = await params
  const provider = getProvider(providerName)

  const { searchParams } = new URL(request.url)
  const code = searchParams.get('code')
  const state = searchParams.get('state')
  const providerError = searchParams.get('error')

  const cookieStore = await cookies()
  const expectedNonce = cookieStore.get('oauth_state')?.value
  cookieStore.delete('oauth_state')

  const loginUrl = new URL('/login', request.url)

  if (!provider || providerError || !code || !state) {
    loginUrl.searchParams.set('oauthError', '로그인이 취소되었거나 실패했습니다.')
    return NextResponse.redirect(loginUrl)
  }

  const [nonce, encodedRedirect] = state.split('.')
  if (!expectedNonce || nonce !== expectedNonce) {
    loginUrl.searchParams.set('oauthError', '로그인 요청이 유효하지 않습니다. 다시 시도해주세요.')
    return NextResponse.redirect(loginUrl)
  }
  const redirectTo = encodedRedirect ? Buffer.from(encodedRedirect, 'base64url').toString() : '/'

  try {
    const accessToken = await provider.exchangeCode(code)
    const profile = await provider.fetchProfile(accessToken)

    if (!profile.email) {
      loginUrl.searchParams.set('oauthError', '이메일 제공에 동의해야 로그인할 수 있습니다.')
      return NextResponse.redirect(loginUrl)
    }

    const syncResponse = await fetch(`${BACKEND_URL}/api/auth/oauth-sync`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Internal-Secret': INTERNAL_SYNC_SECRET },
      body: JSON.stringify({
        provider: providerName.toUpperCase(),
        providerId: profile.providerId,
        email: profile.email,
      }),
    })

    if (!syncResponse.ok) {
      loginUrl.searchParams.set('oauthError', '로그인 처리 중 오류가 발생했습니다.')
      return NextResponse.redirect(loginUrl)
    }

    const { token, email } = (await syncResponse.json()) as { token: string; email: string }

    const response = NextResponse.redirect(new URL(redirectTo, request.url))
    response.cookies.set('session', token, {
      httpOnly: true,
      sameSite: 'lax',
      path: '/',
      maxAge: 60 * 60 * 24 * 7,
    })
    response.cookies.set('session_email', email, {
      httpOnly: false,
      sameSite: 'lax',
      path: '/',
      maxAge: 60 * 60 * 24 * 7,
    })
    return response
  } catch {
    loginUrl.searchParams.set('oauthError', '로그인 처리 중 오류가 발생했습니다.')
    return NextResponse.redirect(loginUrl)
  }
}
