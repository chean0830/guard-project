import { randomUUID } from 'node:crypto'
import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { buildAuthorizeUrl, getProvider } from '@/app/lib/oauth-providers'

/**
 * "구글로 로그인" 버튼이 가리키는 주소. 여기서 CSRF 방지용 state를 만들어 쿠키에 잠깐
 * 저장해두고, 실제 로그인 후 돌아올 위치(redirect)도 state에 실어서 콜백까지 들고 간다.
 */
export async function GET(request: Request, { params }: { params: Promise<{ provider: string }> }) {
  const { provider: providerName } = await params
  const provider = getProvider(providerName)
  if (!provider) {
    return NextResponse.redirect(new URL('/login', request.url))
  }

  const redirectTo = new URL(request.url).searchParams.get('redirect') ?? '/'
  const nonce = randomUUID()
  const state = `${nonce}.${Buffer.from(redirectTo).toString('base64url')}`

  const cookieStore = await cookies()
  cookieStore.set('oauth_state', nonce, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 10,
  })

  // 앱(Flutter)에서 시작한 로그인: 앱이 만든 PKCE challenge를 콜백까지 들고 간다. 웹 로그인에서는 지워서,
  // 앱 로그인을 하다 만 브라우저로 나중에 웹 로그인을 해도 앱으로 튕기지 않게 한다.
  const appChallenge = new URL(request.url).searchParams.get('app_challenge')
  if (appChallenge && /^[A-Za-z0-9_-]{43}$/.test(appChallenge)) {
    cookieStore.set('oauth_app_challenge', appChallenge, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
      maxAge: 60 * 10,
    })
  } else {
    cookieStore.delete('oauth_app_challenge')
  }

  const authorizeUrl = buildAuthorizeUrl(providerName, state)
  return NextResponse.redirect(authorizeUrl ?? new URL('/login', request.url))
}
