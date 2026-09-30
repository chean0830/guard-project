import { redirect } from 'next/navigation'

// 관리자도 일반 로그인 화면에서 로그인한다 (관리자 계정이면 로그인 후 /admin으로 이동).
export default function AdminLoginPage() {
  redirect('/login?redirect=/admin')
}
