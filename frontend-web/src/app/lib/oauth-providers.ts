// 이 파일은 서버(Route Handler, 로그인/회원가입 페이지의 서버 컴포넌트)에서만 import한다 —
// 클라이언트 컴포넌트에서 import하면 시크릿이 번들에 섞일 수 있으니 주의.

export type OAuthProviderName = 'google' | 'kakao' | 'naver'

/** emailVerified: 그 서비스가 이메일 소유를 확인했는지. 백엔드는 이 값이 true일 때만 같은 이메일의 기존 계정과 합친다. */
export type OAuthProfile = { providerId: string; email: string | null; emailVerified: boolean }

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
    const data = (await response.json()) as { sub: string; email?: string; email_verified?: boolean }
    return { providerId: data.sub, email: data.email ?? null, emailVerified: data.email_verified === true }
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
    const data = (await response.json()) as {
      id: number
      kakao_account?: { email?: string; is_email_verified?: boolean; is_email_valid?: boolean }
    }
    const account = data.kakao_account
    return {
      providerId: String(data.id),
      email: account?.email ?? null,
      emailVerified: account?.is_email_verified === true && account?.is_email_valid !== false,
    }
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
    const email = data.response?.email ?? null
    // 네이버는 인증 여부를 따로 주지 않는다. 네이버 자체 메일함(@naver.com) 주소만 소유가 확인된 것으로 본다.
    return { providerId: data.response?.id ?? '', email, emailVerified: Boolean(email?.toLowerCase().endsWith('@naver.com')) }
  },
}

const PROVIDERS: Record<OAuthProviderName, ProviderConfig> = { google, kakao, naver }

/** 키가 설정된(이 프로젝트용 앱을 등록한) provider만 쓴다. 키가 없으면 버튼도 숨기고 로그인 시작도 막는다. */
function isConfigured(provider: ProviderConfig): boolean {
  // 카카오는 Client Secret을 끌 수 있어(끄면 토큰 교환에 보내지 않음) 앱 키만 있어도 쓴다.
  if (provider === kakao) return Boolean(provider.clientId)
  return Boolean(provider.clientId && provider.clientSecret)
}

export function getProvider(name: string): ProviderConfig | null {
  if (name === 'google' || name === 'kakao' || name === 'naver') {
    return isConfigured(PROVIDERS[name]) ? PROVIDERS[name] : null
  }
  return null
}

export function configuredProviders(): OAuthProviderName[] {
  return (Object.keys(PROVIDERS) as OAuthProviderName[]).filter((name) => isConfigured(PROVIDERS[name]))
}

export function buildAuthorizeUrl(name: string, state: string): string | null {
  const provider = getProvider(name)
  if (!provider) return null
  return `${provider.authorizeUrl}?${provider.buildAuthorizeParams(state).toString()}`
}
