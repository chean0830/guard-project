import Link from 'next/link'

export function LawyerCta({ className, label }: { className?: string; label: string }) {
  return (
    <Link href="/consult" className={className}>
      {label}
    </Link>
  )
}
