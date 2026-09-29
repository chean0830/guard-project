import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

async function authHeader(): Promise<Record<string, string>> {
  const cookieStore = await cookies()
  const token = cookieStore.get('session')?.value
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function POST(request: Request) {
  const body = await request.text()
  try {
    const response = await fetch(`${BACKEND_URL}/api/push/subscriptions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body,
    })
    return new NextResponse(await response.text(), { status: response.status })
  } catch {
    return NextResponse.json({ message: '서버에 연결할 수 없습니다.' }, { status: 502 })
  }
}

export async function DELETE(request: Request) {
  const body = await request.text()
  try {
    const response = await fetch(`${BACKEND_URL}/api/push/subscriptions`, {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json', ...(await authHeader()) },
      body,
    })
    return new NextResponse(await response.text(), { status: response.status })
  } catch {
    return NextResponse.json({ message: '서버에 연결할 수 없습니다.' }, { status: 502 })
  }
}
