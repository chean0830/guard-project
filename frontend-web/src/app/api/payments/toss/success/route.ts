import { NextResponse, type NextRequest } from 'next/server'

const BACKEND_URL = process.env.BACKEND_API_URL ?? 'http://localhost:8080'

/**
 * 토스 결제창에서 결제 인증이 끝나면 이 주소(successUrl)로 돌아온다. 쿼리의 amount는 브라우저가
 * 바꿀 수 있는 값이라 믿지 않고, 백엔드가 자기 주문 금액과 대조한 뒤에만 토스에 승인을 요청한다.
 * 승인이 끝나면 변호사 선택 화면으로 보낸다.
 */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams
  const paymentKey = params.get('paymentKey')
  const orderId = params.get('orderId')
  const amount = Number(params.get('amount'))
  const chooseUrl = new URL('/consult/choose', request.url)

  const token = request.cookies.get('session')?.value
  if (!token) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('redirect', '/consult/choose')
    return NextResponse.redirect(loginUrl)
  }
  if (!paymentKey || !orderId || !Number.isFinite(amount)) {
    chooseUrl.searchParams.set('paymentError', '결제 정보가 올바르지 않습니다.')
    return NextResponse.redirect(chooseUrl)
  }

  try {
    const response = await fetch(`${BACKEND_URL}/api/payments/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ paymentKey, orderId, amount }),
    })
    if (!response.ok) {
      chooseUrl.searchParams.set('paymentError', (await response.text()) || '결제 승인에 실패했습니다.')
      return NextResponse.redirect(chooseUrl)
    }
  } catch {
    chooseUrl.searchParams.set('paymentError', '결제 승인 중 서버에 연결할 수 없습니다.')
    return NextResponse.redirect(chooseUrl)
  }

  chooseUrl.searchParams.set('paid', '1')
  return NextResponse.redirect(chooseUrl)
}
