import { NextResponse } from 'next/server'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

export async function GET() {
  try {
    const response = await fetch(`${BACKEND_URL}/api/push/vapid-public-key`, { cache: 'no-store' })
    if (!response.ok) {
      return NextResponse.json({ publicKey: '' }, { status: 200 })
    }
    const data = (await response.json()) as { publicKey: string }
    return NextResponse.json(data)
  } catch {
    return NextResponse.json({ publicKey: '' }, { status: 200 })
  }
}
