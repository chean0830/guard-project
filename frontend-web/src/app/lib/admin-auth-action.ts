'use server'

import { redirect } from 'next/navigation'
import { clearAdminSession } from '@/app/lib/admin-session'

export async function adminLogoutAction() {
  await clearAdminSession()
  redirect('/login')
}
