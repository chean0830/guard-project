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

  const authorizeUrl = buildAuthorizeUrl(providerName, state)
  return NextResponse.redirect(authorizeUrl ?? new URL('/login', request.url))
}
