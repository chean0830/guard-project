const PROVIDERS = [
  { id: 'google', label: 'Google로 계속하기', className: 'border border-zinc-300 text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800' },
  { id: 'kakao', label: '카카오로 계속하기', className: 'bg-[#FEE500] text-black hover:brightness-95' },
  { id: 'naver', label: '네이버로 계속하기', className: 'bg-[#03C75A] text-white hover:brightness-95' },
] as const

export function SocialLoginButtons({ redirectTo }: { redirectTo: string }) {
  return (
    <div className="flex flex-col gap-2">
      {PROVIDERS.map((provider) => (
        <a
          key={provider.id}
          href={`/api/oauth/${provider.id}?redirect=${encodeURIComponent(redirectTo)}`}
          className={`flex items-center justify-center rounded-full px-4 py-2.5 text-sm font-semibold transition-colors ${provider.className}`}
        >
          {provider.label}
        </a>
      ))}
    </div>
  )
}
