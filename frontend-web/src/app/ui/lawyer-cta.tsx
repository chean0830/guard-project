'use client'

import { useRouter } from 'next/navigation'
import { pickRandomLawyerId } from '@/app/lib/lawyers'

export function LawyerCta({ className, label }: { className?: string; label: string }) {
  const router = useRouter()

  function handleClick() {
    const lawyerId = pickRandomLawyerId()
    router.push(`/consult/${lawyerId}`)
  }

  return (
    <button type="button" onClick={handleClick} className={className}>
      {label}
    </button>
  )
}
