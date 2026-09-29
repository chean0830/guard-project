// 이 파일은 Route Handler(app/api/oauth/**)에서만 import한다 — Route Handler는 원래
// 서버 전용이라 클라이언트 번들에 섞일 일이 없어 별도의 server-only 가드는 두지 않았다.

export type OAuthProviderName = 'google' | 'kakao' | 'naver'

export type OAuthProfile = { providerId: string; email: string | null }

const BASE_URL = process.env.OAUTH_BASE_URL ?? 'http://localhost:3000'

function redirectUriFor(provider: OAuthProviderName): string {
  return `${BASE_URL}/api/oauth/${provider}/callback`
}

type ProviderConfig = {
  clientId: string
  clientSecret: string
  authorizeUrl: string
  scope: string
  buildAuthorizeParams: (state: string) => URLSearchParams
  exchangeCode: (code: string) => Promise<string>
  fetchProfile: (accessToken: string) => Promise<OAuthProfile>
}

async function postForm(url: string, body: Record<string, string>): Promise<Record<string, unknown>> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(body),
  })
  if (!response.ok) {
    throw new Error(`토큰 교환 실패 (${response.status})`)
  }
  return response.json()
}

const google: ProviderConfig = {
  clientId: process.env.GOOGLE_CLIENT_ID ?? '',
  clientSecret: process.env.GOOGLE_CLIENT_SECRET ?? '',
  authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
  scope: 'openid email',
  buildAuthorizeParams(state) {
    return new URLSearchParams({
      client_id: this.clientId,
      redirect_uri: redirectUriFor('google'),
      response_type: 'code',
      scope: this.scope,
      state,
    })
  },
  async exchangeCode(code) {
    const data = await postForm('https://oauth2.googleapis.com/token', {
      code,
      client_id: this.clientId,
      client_secret: this.clientSecret,
      redirect_uri: redirectUriFor('google'),
      grant_type: 'authorization_code',
    })
    return data.access_token as string
  },
  async fetchProfile(accessToken) {
    const response = await fetch('https://openidconnect.googleapis.com/v1/userinfo', {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    const data = (await response.json()) as { sub: string; email?: string }
    return { providerId: data.sub, email: data.email ?? null }
  },
}

const kakao: ProviderConfig = {
  clientId: process.env.KAKAO_CLIENT_ID ?? '',
  clientSecret: process.env.KAKAO_CLIENT_SECRET ?? '',
  authorizeUrl: 'https://kauth.kakao.com/oauth/authorize',
  scope: 'account_email',
  buildAuthorizeParams(state) {
    return new URLSearchParams({
      client_id: this.clientId,
      redirect_uri: redirectUriFor('kakao'),
      response_type: 'code',
      scope: this.scope,
      state,
    })
  },
  async exchangeCode(code) {
    const body: Record<string, string> = {
      grant_type: 'authorization_code',
      client_id: this.clientId,
      redirect_uri: redirectUriFor('kakao'),
      code,
    }
    if (this.clientSecret) {
      body.client_secret = this.clientSecret
    }
    const data = await postForm('https://kauth.kakao.com/oauth/token', body)
    return data.access_token as string
  },
  async fetchProfile(accessToken) {
    const response = await fetch('https://kapi.kakao.com/v2/user/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    const data = (await response.json()) as { id: number; kakao_account?: { email?: string } }
    return { providerId: String(data.id), email: data.kakao_account?.email ?? null }
  },
}

const naver: ProviderConfig = {
  clientId: process.env.NAVER_CLIENT_ID ?? '',
  clientSecret: process.env.NAVER_CLIENT_SECRET ?? '',
  authorizeUrl: 'https://nid.naver.com/oauth2.0/authorize',
  scope: '',
  buildAuthorizeParams(state) {
    return new URLSearchParams({
      client_id: this.clientId,
      redirect_uri: redirectUriFor('naver'),
      response_type: 'code',
      state,
    })
  },
  async exchangeCode(code) {
    const data = await postForm('https://nid.naver.com/oauth2.0/token', {
      grant_type: 'authorization_code',
      client_id: this.clientId,
      client_secret: this.clientSecret,
      code,
    })
    return data.access_token as string
  },
  async fetchProfile(accessToken) {
    const response = await fetch('https://openapi.naver.com/v1/nid/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    const data = (await response.json()) as { response?: { id: string; email?: string } }
    return { providerId: data.response?.id ?? '', email: data.response?.email ?? null }
  },
}

const PROVIDERS: Record<OAuthProviderName, ProviderConfig> = { google, kakao, naver }

export function getProvider(name: string): ProviderConfig | null {
  if (name === 'google' || name === 'kakao' || name === 'naver') {
    return PROVIDERS[name]
  }
  return null
}

export function buildAuthorizeUrl(name: string, state: string): string | null {
  const provider = getProvider(name)
  if (!provider) return null
  return `${provider.authorizeUrl}?${provider.buildAuthorizeParams(state).toString()}`
}
