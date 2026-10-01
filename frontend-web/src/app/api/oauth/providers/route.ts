import { NextResponse } from 'next/server'
import { configuredProviders } from '@/app/lib/oauth-providers'

/** 앱(Flutter)이 소셜 로그인 버튼을 그릴 때 쓰는, 키가 설정된 provider 목록. 이름만 내려주고 키는 내려주지 않는다.
 *  Route Handler는 기본적으로 캐시되지 않아 실행 시점의 키 설정을 그대로 반영한다. */
export function GET() {
  return NextResponse.json({ providers: configuredProviders() })
}
