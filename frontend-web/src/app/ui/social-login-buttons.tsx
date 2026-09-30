function GoogleLogo() {
  return (
    <svg viewBox="0 0 48 48" className="h-[18px] w-[18px]" aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  )
}

function KakaoLogo() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" aria-hidden="true">
      <path
        fill="#000000"
        d="M12 3C6.48 3 2 6.54 2 10.9c0 2.83 1.88 5.3 4.7 6.7-.2.72-.74 2.64-.85 3.05-.13.5.19.5.39.36.16-.1 2.5-1.7 3.52-2.39.73.1 1.48.16 2.24.16 5.52 0 10-3.54 10-7.9S17.52 3 12 3z"
      />
    </svg>
  )
}

function NaverLogo() {
  return (
    <svg viewBox="0 0 24 24" className="h-[14px] w-[14px]" aria-hidden="true">
      <path fill="#FFFFFF" d="M16.273 12.845 7.376 0H0v24h7.726V11.156L16.624 24H24V0h-7.727v12.845z" />
    </svg>
  )
}

// 각 사의 로그인 버튼 가이드(배경색·로고)를 따른다: 구글은 흰 바탕+컬러 G, 카카오는 #FEE500+검은 말풍선, 네이버는 #03C75A+흰 N.
const PROVIDERS = [
  {
    id: 'google',
    label: 'Google로 계속하기',
    Logo: GoogleLogo,
    className:
      'border border-zinc-300 bg-white text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:bg-zinc-800',
  },
  { id: 'kakao', label: '카카오로 계속하기', Logo: KakaoLogo, className: 'bg-[#FEE500] text-black/85 hover:brightness-95' },
  { id: 'naver', label: '네이버로 계속하기', Logo: NaverLogo, className: 'bg-[#03C75A] text-white hover:brightness-95' },
] as const

export type SocialProvider = (typeof PROVIDERS)[number]['id']

export function SocialLoginButtons({ redirectTo, enabled }: { redirectTo: string; enabled: SocialProvider[] }) {
  return (
    <div className="flex flex-col gap-2">
      {PROVIDERS.filter((p) => enabled.includes(p.id)).map(({ id, label, Logo, className }) => (
        <a
          key={id}
          href={`/api/oauth/${id}?redirect=${encodeURIComponent(redirectTo)}`}
          className={`relative flex items-center justify-center rounded-full px-4 py-2.5 text-sm font-semibold transition-colors ${className}`}
        >
          <span className="absolute left-5 flex items-center">
            <Logo />
          </span>
          {label}
        </a>
      ))}
    </div>
  )
}
