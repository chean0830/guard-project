import { redirect } from 'next/navigation'

// 변호사 승인 화면은 통합 관리자 페이지(/admin)의 한 탭으로 옮겼다. 기존 주소로 들어와도 이어지게 둔다.
export default function AdminLawyersPage() {
  redirect('/admin')
}
